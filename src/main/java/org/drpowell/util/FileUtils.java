package org.drpowell.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public class FileUtils {
	public static BufferedReader filenameToBufferedReader(String filename) throws IOException {
		if (filename.endsWith(".gz") || filename.endsWith(".GZ")) {
			return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(filename)))));
		} else {
			return new BufferedReader(new FileReader(new File(filename)));
		}
	}

	public static URL findExistingFile(String f, File... otherDirectories) {
		try {
			if (new File(f).exists()) return new File(f).toURI().toURL();
			Logger logger = Logger.getLogger("VARITAS");
			File attempt;
			for (File dir : otherDirectories) {
				attempt = new File(dir, f);
				logger.config("Trying " + attempt.getPath());
				if (attempt.exists()) return attempt.toURI().toURL();
			}
			attempt = new File(System.getProperty("user.dir"), f);
			logger.config("Trying " + attempt.getPath());
			if (attempt.exists()) return attempt.toURI().toURL();
	
			URL resource = ClassLoader.getSystemResource(f);
			if (resource != null) return resource;
	
			attempt = new File(System.getProperty("user.home"), f);
			logger.config("Trying " + attempt.getPath());
			if (attempt.exists()) return attempt.toURI().toURL();
		} catch (MalformedURLException e) {
			System.err.println(e);
		}
		
		// give up
		return null;
	}
}
