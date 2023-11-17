package fr.guiguilechat.jx4f.x4model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import fr.guiguilechat.jx4f.x4model.data.CachedX4Data;
import fr.lelouet.tools.application.xdg.XDGApp;
import lombok.Getter;
import lombok.experimental.Accessors;

public class X4Cache {

	public static final X4Cache INSTANCE = new X4Cache();

	public static final String APNAME_ENV = "jx4f.appname";
	public static final String CACHEDIR_ENV = "jx4f.cachedir";
	public static final String X4MAINDIR_ENV = "jx4f.maindir";

	public static void main(String[] args) {
		System.out.println("game data are in : " + INSTANCE.x4Dir());
		System.out.println("cache is : " + INSTANCE.cacheDir());
		INSTANCE.mainData().forEach(CachedX4Data::extract);
		INSTANCE.extensionData().forEach(CachedX4Data::extract);
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final XDGApp app = new XDGApp(
			System.getenv().getOrDefault(APNAME_ENV, "lechatguigui.jx4f.model"));

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final File cacheDir = app().cacheFile("x4data");

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final File x4Dir = findX4Directory();

	/**
	 * list of dirs we may find the install under our home
	 */
	public static String[] HOMESUBDIRS = {
			".steam/debian-installation/steamapps/common/X4 Foundations",
			".steam/steam/steamapps/common/X4 Foundations/" };
	public static String[] PROGRAMSUBDIRS = { "Steam/steamapps/common/X4 Foundations" };

	protected File findX4Directory() {

		if (System.getProperty(X4MAINDIR_ENV) != null) {
			File file = new File(System.getProperty(X4MAINDIR_ENV));
			return file.isDirectory() ? file : null;
		}

		ArrayList<File> potentialDirs = new ArrayList<>();

		String homePath = System.getProperty("user.home");
		if (homePath != null) {
			File homeDir = new File(homePath);
			if (homeDir.isDirectory()) {
				Stream.of(HOMESUBDIRS).map(s -> new File(homeDir, s)).filter(File::isDirectory).forEach(potentialDirs::add);
			}
		}

		String prog86Path = System.getProperty("PROGRAMFILES(X86)");
		if (prog86Path != null) {
			File progDir86 = new File(prog86Path);
			if (progDir86.isDirectory()) {
				Stream.of(PROGRAMSUBDIRS).map(s -> new File(progDir86, s)).filter(File::isDirectory)
						.forEach(potentialDirs::add);
			}
		}

		String progPath = System.getProperty("PROGRAMFILES");
		if (progPath != null) {
			File progDir = new File(progPath);
			if (progDir.isDirectory()) {
				Stream.of(PROGRAMSUBDIRS).map(s -> new File(progDir, s)).filter(File::isDirectory).forEach(potentialDirs::add);
			}
		}

		if (potentialDirs.isEmpty()) {
			return null;
		}
		// sort by children size decreasing. So the first one is the one with the
		// most children.
		Collections.sort(potentialDirs, Comparator.comparing(file -> -file.listFiles().length));
		return potentialDirs.get(0);
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<CachedX4Data> mainData = listMainData();

	protected List<CachedX4Data> listMainData() {
		File baseDir = x4Dir();
		File cacheDir = cacheDir();
		File[] dataFiles = baseDir.listFiles(f -> f.isFile() && f.getName().endsWith(".cat"));
		return Stream.of(dataFiles)
				.map(f -> CachedX4Data.of(baseDir, f.getName().replace(".cat", ""), cacheDir)).toList();
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<CachedX4Data> extensionData = listExtensionsData();

	protected List<CachedX4Data> listExtensionsData() {
		File baseDir = new File(x4Dir(), "extensions");
		File baseCacheDir = new File(cacheDir(), "extensions");
		Stream<CachedX4Data> ret = Stream.empty();
		for (File extDir : baseDir.listFiles()) {
			if (!extDir.isDirectory()) {
				continue;
			}
			File[] dataFiles = extDir.listFiles(f -> f.isFile() && f.getName().endsWith(".cat"));
			if (dataFiles.length > 0) {
				ret = Stream.concat(ret, Stream.of(dataFiles)
						.map(f -> CachedX4Data.of(extDir, f.getName().replace(".cat", ""), baseCacheDir)));
			}
		}
		return ret.toList();
	}
}
