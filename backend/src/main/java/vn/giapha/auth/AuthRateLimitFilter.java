package vn.giapha.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.giapha.common.ApiError;

/** Giới hạn tần suất (cửa sổ trượt, theo IP) cho login / register / refresh. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Limit(int max, long windowMs) {}

    private static final Map<String, Limit> LIMITS = Map.of(
        "/api/v1/auth/login", new Limit(10, 60_000L),
        "/api/v1/auth/register", new Limit(5, 3_600_000L),
        "/api/v1/auth/refresh", new Limit(30, 60_000L));
    private static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final ObjectMapper om;
    private final boolean enabled;

    public AuthRateLimitFilter(Clock clock, ObjectMapper om, @Value("${app.rate-limit.enabled}") boolean enabled) {
        this.clock = clock;
        this.om = om;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = enabled && "POST".equals(req.getMethod()) ? LIMITS.get(req.getRequestURI()) : null;
        if (limit == null) { chain.doFilter(req, res); return; }

        long now = clock.millis();
        if (hits.size() > MAX_KEYS) evict(now);
        Deque<Long> q = hits.computeIfAbsent(req.getRequestURI() + "|" + req.getRemoteAddr(), k -> new ArrayDeque<>());
        long retryAfterMs = 0;
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() <= now - limit.windowMs()) q.pollFirst();
            if (q.size() >= limit.max()) retryAfterMs = q.peekFirst() + limit.windowMs() - now;
            else q.addLast(now);
        }
        if (retryAfterMs > 0) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(Math.max(1, (retryAfterMs + 999) / 1000)));
            res.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            om.writeValue(res.getOutputStream(), new ApiError("TOO_MANY_REQUESTS", "Thao tác quá nhiều lần, vui lòng thử lại sau", null));
            return;
        }
        chain.doFilter(req, res);
    }

    /** Xoá các entry đã hết hiệu lực để map không phình vô hạn. */
    private void evict(long now) {
        hits.entrySet().removeIf(e -> {
            String path = e.getKey().substring(0, e.getKey().indexOf('|'));
            long windowMs = LIMITS.get(path).windowMs();
            Deque<Long> q = e.getValue();
            synchronized (q) { return q.isEmpty() || q.peekLast() <= now - windowMs; }
        });
    }
}
