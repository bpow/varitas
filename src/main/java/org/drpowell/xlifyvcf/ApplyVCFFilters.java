package org.drpowell.xlifyvcf;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.varitas.CLIRunnable;
import org.drpowell.varitas.CompoundMutationFilter;
import org.drpowell.varitas.Main;
import org.drpowell.varitas.MendelianConstraintFilter;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

public class ApplyVCFFilters implements CLIRunnable {
	private VCFParser vcfParser;
	private VCFIterator variants;
	
	@Argument(alias = "f", description = "script file(s) by which to filter variants, delimited by commas", delimiter = ",")
	private String[] filters;
	
	@Argument(alias = "j", description = "javascript to apply to each variant as a filter (if result is true, the variant passes)")
	private String javascriptFilter;
	
	@Argument(alias = "i", description = "input file of variants (VCF format)")
	private String input;
	
	@Argument(alias = "o", description = "output (.vcf) file")
	private String output;
	
	@Argument(alias = "b", description = "apply biallelic filter")
	private static Boolean applyBiallelic = false;
	
	@Argument(alias = "m", description = "apply mendelian constraint filter")
	private static Boolean applyMendelianConstraint = false;
	
	@Argument(alias = "a", description = "file with additional headers to add to input vcf file")
	private String additionalHeaders;

	public ApplyVCFFilters() {
	}
	
	protected ApplyVCFFilters initialize(VCFParser parser) {
		vcfParser = parser;
		VCFHeaders vcfHeaders = parser.getHeaders();
		if (additionalHeaders != null) {
			URL url = Main.findExistingFile(additionalHeaders);
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
				String line = null;
				while ((line = br.readLine()) != null) {
					vcfHeaders.add(new VCFMeta(line));
				}
				br.close();
			} catch (IOException ioe) {
				Logger.getLogger("VARITAS").log(Level.SEVERE, "Problem reading additional headers from " + url, ioe);
			}
		}
		variants = vcfParser;
		if (javascriptFilter != null && !"".equals(javascriptFilter)) {
			variants = new JavascriptBooleanVCFFilter(variants, javascriptFilter);
		}
		if (filters != null) {
			for (String filter : filters) {
				URL url = Main.findExistingFile(filter);
				Reader r;
				try {
					r = new InputStreamReader(url.openStream());
					variants = new ScriptVCFFilter(variants, r);
				} catch (IOException e) {
					Logger.getLogger("VARITAS").warning("The filter [ " + filter + " ] could not be found and will be ignored (tried [ " + url + " ])");
				}
			}
		}
		if (applyBiallelic) {
			List<int []> trios = VCFUtils.getTrioIndices(vcfHeaders);
			// FIXME-- only handles a single trio
			if (!trios.isEmpty()) {
				variants = new CompoundMutationFilter(variants, trios.get(0));
				vcfHeaders = variants.getHeaders();
			}
		}
		if (applyMendelianConstraint) {
			variants = new MendelianConstraintFilter(variants, vcfParser.getHeaders());
			vcfHeaders = variants.getHeaders();
		}
		return this;
	}
	
	public void doMain(List<String> extraArgs) {
		try {
			BufferedReader reader;
			if (input != null) {
				reader = GunzipIfGZipped.filenameToBufferedReader(input);
			} else {
				reader = new BufferedReader(new InputStreamReader(System.in));
			}
			initialize(new VCFParser(reader));
			PrintWriter outWriter;
			if (output == null) {
				outWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024));
			} else {
				outWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(output), 1024));
			}
			outWriter.print(vcfParser.getHeaders());
			outWriter.println(vcfParser.getHeaders().getColumnHeaderLine());
			while (variants.hasNext()) {
				outWriter.println(variants.next());
			}
			outWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
			Args.usage(this);
		}
	}

}
