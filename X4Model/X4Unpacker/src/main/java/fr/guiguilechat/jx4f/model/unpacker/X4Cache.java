package fr.guiguilechat.jx4f.model.unpacker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.yaml.snakeyaml.Yaml;

import fr.guiguilechat.jx4f.model.unpacker.data.CachedX4Data;
import fr.lelouet.tools.application.xdg.XDGApp;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * cache of X4 resource<br />
 * Allows to extract x4 dat, from main or extensions, into the cache directory.
 * The cache is updated when the data is.
 */
@Slf4j
public class X4Cache {

	public static final X4Cache INSTANCE = new X4Cache();

	public static final String APNAME_ENV = "jx4f.unpacker.appname";
	public static final String CACHEDIR_ENV = "jx4f.unpacker.cachedir";
	public static final String X4MAINDIR_ENV = "jx4f.unpacker.maindir";

	public static void main(String[] args) {
		System.out.println("game data are in : " + INSTANCE.x4Dir());
		System.out.println("cache is : " + INSTANCE.cacheDir());
		Stream.concat(INSTANCE.mainData().stream(), INSTANCE.extensionData().stream()).parallel()
				.forEach(CachedX4Data::extract);
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final XDGApp app = new XDGApp(System.getenv().getOrDefault(APNAME_ENV, X4Cache.class.getPackageName()));

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final Config config = loadConfig();

	/** load the config if exists, otherwise returns an empty config */
	protected Config loadConfig() {
		File yamlFile = app().configFile("config.yaml");
		if (yamlFile.isFile()) {
			try {
				return new Yaml().loadAs(new FileReader(yamlFile), Config.class);
			} catch (FileNotFoundException e) {
				log.error("while loading yaml file " + yamlFile + " as " + Config.class, e);
			}
		}
		return new Config();
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final File cacheDir = selectCacheDir();

	protected File selectCacheDir() {

		// search for var env first
		if (System.getProperty(CACHEDIR_ENV) != null) {
			return new File(System.getProperty(CACHEDIR_ENV));
		}

		// then use config is specified
		if (config().getX4Cache() != null) {
			return new File(config().getX4Cache());
		}

		// else return default
		return app().cacheFile("x4data");

	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final File x4Dir = findX4Directory();

	/**
	 * list of dirs we may find the install under our home (linux)
	 */
	public static String[] HOMESUBDIRS = {
			".steam/debian-installation/steamapps/common/X4 Foundations",
			".steam/steam/steamapps/common/X4 Foundations/"
	};

	/**
	 * list of subdirs we may find the install under the program files (windows)
	 */
	public static String[] PROGRAMSUBDIRS = {
			"Steam/steamapps/common/X4 Foundations"
	};

	/**
	 * search for the existing X4 directory
	 *
	 * @return the found directory, or null.
	 */
	protected File findX4Directory() {

		// if specified as an env variable, then use it
		if (System.getProperty(X4MAINDIR_ENV) != null) {
			File file = new File(System.getProperty(X4MAINDIR_ENV));
			return file.isDirectory() ? file : null;
		}

		// if present in the config, use it.
		if (config().getX4Dir() != null) {
			File file = new File(config().getX4Dir());
			return file.isDirectory() ? file : null;
		}

		// else try to find
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

	/** list of data resources that are from the main game */
	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<CachedX4Data> mainData = listMainData();

	protected List<CachedX4Data> listMainData() {
		File baseDir = x4Dir();
		File cacheDir = cacheDir();
		File[] dataFiles = baseDir.listFiles(f -> f.isFile() && f.getName().endsWith(".cat"));
		return Stream.of(dataFiles)
				.map(f -> CachedX4Data.of(baseDir, f.getName().replace(".cat", ""), cacheDir))
				.sorted(Comparator.comparing(d -> d.getCatFile().getAbsolutePath()))
				.toList();
	}

	/** list of data resources that are from extensions */
	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<CachedX4Data> extensionData = listExtensionData();

	protected List<CachedX4Data> listExtensionData() {
		File baseDir = new File(x4Dir(), "extensions");
		File baseCacheDir = new File(cacheDir(), "extensions");
		Stream<CachedX4Data> ret = Stream.empty();
		for (File extDir : baseDir.listFiles()) {
			if (!extDir.isDirectory()) {
				continue;
			}
			File[] dataFiles = extDir.listFiles(f -> f.isFile() && f.getName().endsWith(".cat"));
			if (dataFiles.length > 0) {
				String extName = extDir.getName();
				File extCache = new File(baseCacheDir, extName);
				ret = Stream.concat(ret, Stream.of(dataFiles)
						.map(f -> CachedX4Data.of(extDir, f.getName().replace(".cat", ""), extCache)));
			}
		}
		return ret.sorted(Comparator.comparing(d -> d.getCatFile().getAbsolutePath())).toList();
	}

	/** the list of extensions names */
	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<String> extensions = listExtensions();

	protected List<String> listExtensions() {
		File extDir = new File(x4Dir(), "extensions");
		if (!extDir.isFile()) {
			return Collections.emptyList();
		}
		return Stream.of(extDir.listFiles(File::isDirectory)).map(File::getName).toList();
	}
}
