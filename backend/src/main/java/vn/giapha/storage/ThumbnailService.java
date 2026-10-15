package vn.giapha.storage;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.giapha.common.ApiException;

@Service
public class ThumbnailService {
    public byte[] square(byte[] image, int size) {
        BufferedImage src;
        try { src = ImageIO.read(new ByteArrayInputStream(image)); }
        catch (IOException e) { src = null; }
        if (src == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Tệp không phải ảnh hợp lệ");
        int side = Math.min(src.getWidth(), src.getHeight());
        BufferedImage crop = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
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
