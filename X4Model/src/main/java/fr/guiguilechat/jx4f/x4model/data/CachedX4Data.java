package fr.guiguilechat.jx4f.x4model.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * X4 data : couple of cat and dat file. the cacheRoot allows us to access the
 * content of the .dat <br />
 * The entries can be access by listing the entries with {@link #entries}, then
 * loaded with {@link #entryFile(CatLine)}.<br />
 * The first access to {@link #entryFile(CatLine)} triggers the extract of all
 * the entries into the cache. This can also be performed manually with
 * {@link #extract()}
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CachedX4Data {

	@Getter
	private final File catFile;

	@Getter
	private final File datFile;

	@Getter
	private final File cacheDir;

	/**
	 * @param mainDir   the directory of the data. This dir MUST contain name.cat
	 *                  AND name.dat.
	 * @param name      name of the data. eg 01
	 * @param cacheRoot the cache dir to use. If that dir does not end with the
	 *                  data's name, a sub dir is created.
	 *                  This dir MUST be a dir or creatable.
	 * @return
	 */
	public static CachedX4Data of(File mainDir, String name, File cacheRoot) {
		File cat = new File(mainDir, name + ".cat");
		File dat = new File(mainDir, name + ".dat");
		File cache = new File(cacheRoot, name);
		if (!cache.getName().equals(name)) {
			cache = new File(cache, name);
		}
		cache.mkdirs();
		if (!cat.isFile()) {
			throw new RuntimeException("missing file " + cat.getAbsolutePath());
		}
		if (!dat.isFile()) {
			throw new RuntimeException("missing file " + dat.getAbsolutePath());
		}
		if (!cache.isDirectory()) {
			throw new RuntimeException("can't create dir" + cache.getAbsolutePath());
		}
		return new CachedX4Data(cat, dat, cache);
	}

	/**
	 * if the data is from an extension, return the extension name. Otherwise null.
	 */
	@Getter(lazy = true)
	private final String extension = getCatFile().getParentFile().getName().equals("X4 Foundations") ? null
			: getCatFile().getParentFile().getName();

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<CatLine> entries = CatLine.load(catFile);

	private boolean extracted = false;

	public synchronized void extract() {
		if (extracted) {
			return;
		}
		try {
			FileInputStream fis = new FileInputStream(datFile);
			for (CatLine cl : entries()) {
				File outFile = new File(cacheDir, cl.path());
				outFile.getParentFile().mkdirs();
				System.out.println("extracting " + datFile.getName() + "/" + cl.path() + " into " + outFile);
				try (FileOutputStream fos = new FileOutputStream(outFile)) {
					fos.write(fis.readNBytes(cl.bytes()));
				}
			}
			fis.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		extracted = true;
	}

	public File entryFile(CatLine entry) {
		extract();
		return new File(cacheDir, entry.path());
	}

}
