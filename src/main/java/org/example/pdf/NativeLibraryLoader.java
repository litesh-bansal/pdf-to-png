package org.example.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies the bundled native libraries out of {@code src/main/resources/native} into one
 * private directory per renderer, so {@link PdfRenderService} can load N of them independently.
 *
 * <h2>Why copies at all?</h2>
 * <p>
 * pdfium (Google's PDF engine) keeps global state and is not thread-safe, so a single loaded
 * copy can only render one page at a time. The OS dynamic loader ({@code dlopen}) identifies a
 * library by its <em>file</em>: opening the same path twice returns the same already-loaded
 * image, but opening N distinct files maps N separate images, each with its own private copy of
 * every global. N copies therefore behave like N independent pdfium installations in one
 * process and can render in parallel.
 * <p>
 * Both libraries must be duplicated per slot. {@code libpdf_render} holds one global
 * {@code Pdfium} and itself {@code dlopen}s {@code libpdfium}; if every {@code libpdf_render}
 * copy pointed at one {@code libpdfium.dylib} file, the loader would share that single pdfium
 * image between them and the concurrency would silently disappear.
 *
 * <h2>Why extract to a temp directory?</h2>
 * <p>
 * The OS loader needs a real file on disk; it cannot {@code dlopen} an entry inside a jar. So
 * the resources are copied out into a fresh temporary directory, once per slot. The files are
 * marked {@code deleteOnExit}; libraries in use cannot be deleted earlier.
 */
final class NativeLibraryLoader {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryLoader.class);

    /** Classpath location of the prebuilt libraries (macOS arm64). */
    private static final String RESOURCE_DIR = "native/";
    private static final String RENDERER = "libpdf_render.dylib";
    private static final String PDFIUM = "libpdfium.dylib";

    /** The two files that make up one renderer instance. */
    record Slot(Path renderer, Path pdfium) {
    }

    private NativeLibraryLoader() {
    }

    /**
     * Materializes {@code copies} slots, each a directory holding its own copy of both libraries.
     *
     * @param copies how many independent renderers to stage; must be at least 1
     * @throws IOException if a library resource is missing or cannot be copied out
     */
    static List<Slot> load(int copies) throws IOException {
        Path workDir = Files.createTempDirectory("pdf-render-");
        workDir.toFile().deleteOnExit();

        List<Slot> slots = new ArrayList<>(copies);
        for (int i = 0; i < copies; i++) {
            Path dir = Files.createDirectories(workDir.resolve("slot-" + i));
            dir.toFile().deleteOnExit();
            slots.add(new Slot(
                    extract(RENDERER, dir.resolve(RENDERER)),
                    extract(PDFIUM, dir.resolve(PDFIUM))));
        }

        log.info("Staged {} copy/copies of the native libraries under {}", copies, workDir);
        return slots;
    }

    /** Copies one classpath resource to a real file the OS loader can open. */
    private static Path extract(String name, Path target) throws IOException {
        String resource = RESOURCE_DIR + name;
        try (InputStream in = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + resource);
            }

            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        target.toFile().deleteOnExit();
        return target;
    }
}
