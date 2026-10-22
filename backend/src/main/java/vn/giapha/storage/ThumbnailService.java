package vn.giapha.storage;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.giapha.common.ApiException;

@Service
public class ThumbnailService {
    private static final long MAX_PIXELS = 25_000_000L;
    private static final int MAX_SIDE = 12_000;

    public byte[] square(byte[] image, int size) { return squares(image, size).get(0); }

    /** Giải mã một lần, tạo nhiều thumbnail vuông. */
    public List<byte[]> squares(byte[] image, int... sizes) {
        BufferedImage src = decode(image);
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage crop = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        List<byte[]> result = new ArrayList<>();
        for (int size : sizes) result.add(render(crop, size));
        return result;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Tệp không phải ảnh hợp lệ");
    }

    private BufferedImage decode(byte[] image) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            if (in == null) throw invalid();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) throw invalid();
                if (w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Kích thước ảnh quá lớn");
                }
                BufferedImage img = reader.read(0);
                if (img == null) throw invalid();
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException ae) throw ae;
            throw invalid();
        }
    }

    private byte[] render(BufferedImage crop, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.drawImage(crop, 0, 0, size, size, null);
        g.dispose();
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
