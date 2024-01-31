package util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

class ZipperTest {


    @Test
    void testZipper() throws IOException {
        new Zipper(Path.of("../../JobRunrPro/core"), Path.of("./jobrunr-pro-source/test.zip"))
                .excludeFolders("bin", "build", "node_modules")
                .zip();

    }
}