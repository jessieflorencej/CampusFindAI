package com.campusfind.ai;
import java.nio.file.Path;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;
@Service
public class UnavailableImageMatchingService implements ImageMatchingService {
    public OptionalDouble similarity(Path lostImage,Path foundImage){return OptionalDouble.empty();}
}
