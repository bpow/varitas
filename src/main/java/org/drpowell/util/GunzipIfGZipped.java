package org.drpowell.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

public class GunzipIfGZipped {
	public static BufferedReader filenameToBufferedReader(String filename) throws IOException {
		if (filename.endsWith(".gz") || filename.endsWith(".GZ")) {
			return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(filename)))));
		} else {
			return new BufferedReader(new FileReader(new File(filename)));
		}
	}
}
