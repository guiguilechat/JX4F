package fr.guiguilechat.jx4f.x4model.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * structure of a .cat file
 */
public record CatLine(String path, int bytes, long epoch, String hash) {

	public static CatLine of(String line) {
		String[] split = line.split(" ");
		String path = List.of(split).subList(0, split.length - 3).stream().collect(Collectors.joining(" "));
		return new CatLine(path, Integer.parseInt(split[split.length - 3]), Long.parseLong(split[split.length - 2]),
				split[split.length - 1]);
	}

	public static List<CatLine> load(File catFile) {
		try {
			return Files.lines(catFile.toPath()).map(CatLine::of).toList();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}