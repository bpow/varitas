package org.drpowell.varitas;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class GenePresenceAnnotator extends Annotator {
	public final String fileName;
	public final String annotatorName;
	private final HashSet<String> geneNames = new HashSet<String>();
	public final VCFInfoHeaderLine infoLine;
	
	// FIXME - handle multiple columns
	
	public GenePresenceAnnotator(String annotatorName, String inputFile) throws IOException {
		this.annotatorName = annotatorName;
		fileName = inputFile;
		BufferedReader br = new BufferedReader(new FileReader(inputFile));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.equals("")) {
				geneNames.add(line);
			}
		}
		br.close();
		String basename = new File(inputFile).getName();
		infoLine = new VCFInfoHeaderLine(annotatorName, 1, VCFHeaderLineType.String, "Gene present in the file " + basename);
	}
	
	
	// FIXME - allow a different Gene_name title
	
	@Override
	public VariantContext annotate(VariantContext variant) {
		String varGenes = variant.getAttributeAsString("Gene_name", null);
		if (varGenes != null) {
			VariantContextBuilder builder = new VariantContextBuilder(variant);
			for (String vg: varGenes.split(",")) {
				if (geneNames.contains(vg)) {
					// FIXME - handle multiple matches
					builder.attribute(annotatorName, vg);
				}
			}
			variant = builder.make();
		}
		return variant;
	}

	@Override
	public Iterable<VCFInfoHeaderLine> infoLines() {
		return Arrays.asList(infoLine);
	}
	
	@Override
	public String toString() {
		return "GeneAnnotator: " + fileName;
	}

}
