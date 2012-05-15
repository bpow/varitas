package org.drpowell.grandannotator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class GeneAnnotator extends Annotator {
	public final String fileName;
	public final String annotatorName;
	private final HashSet<String> geneNames = new HashSet<String>();
	public final String infoLine;
	
	// FIXME - handle multiple columns
	
	public GeneAnnotator(String annotatorName, String inputFile) throws IOException {
		this.annotatorName = annotatorName;
		fileName = inputFile;
		BufferedReader br = new BufferedReader(new FileReader(inputFile));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.equals("")) {
				geneNames.add(line);
			}
		}
		String basename = new File(inputFile).getName();
		infoLine = "##INFO=<ID=" + annotatorName + ",Number=1,Type=String,Description=\"Gene present in the file " + basename + "\">";
	}
	
	// FIXME - allow a different Gene_name title
	
	@Override
	public Map<String, Object> annotate(String chromosome, int start, int end,
			String ref, String alt, Map<String, Object> info) {
		String varGenes = (String) info.get("Gene_name");
		if (varGenes != null) {
			for (String vg: varGenes.split(",")) {
				if (geneNames.contains(vg)) {
					// FIXME - handle multiple matches
					info.put(annotatorName, vg);
				}
			}
		}
		return info;
	}

	@Override
	public Iterable<String> infoLines() {
		LinkedList<String> l = new LinkedList<String>();
		l.add(infoLine);
		return l;
	}
	
	@Override
	public String toString() {
		return "GeneAnnotator: " + fileName;
	}

}
