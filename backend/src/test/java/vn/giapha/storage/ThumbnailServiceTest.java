package vn.giapha.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vn.giapha.common.ApiException;

class ThumbnailServiceTest {
    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.RED); g.fillRect(0, 0, w, h); g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void landscapeImageBecomesSquareJpegOfRequestedSize() throws Exception {
        byte[] out = new ThumbnailService().square(png(400, 200), 96);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
        assertThat(img.getWidth()).isEqualTo(96);
        assertThat(img.getHeight()).isEqualTo(96);
        assertThat(out[0] & 0xFF).isEqualTo(0xFF);       // chữ ký JPEG FFD8
        assertThat(out[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void smallImageIsStillScaledToSize() throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(new ThumbnailService().square(png(20, 20), 256)));
        assertThat(img.getWidth()).isEqualTo(256);
    }

    @Test
    void nonImageBytesAreRejected() {
        assertThatThrownBy(() -> new ThumbnailService().square("not an image".getBytes(), 96))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }
}
