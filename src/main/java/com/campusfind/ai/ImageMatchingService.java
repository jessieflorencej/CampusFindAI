package com.campusfind.ai;

import java.nio.file.Path;
import java.util.OptionalDouble;

/** Extension point for a reviewed visual model. Empty means no image score is available. */
public interface ImageMatchingService {
    OptionalDouble similarity(Path lostImage, Path foundImage);
}
