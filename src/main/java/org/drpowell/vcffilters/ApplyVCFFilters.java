package org.drpowell.vcffilters;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drpowell.util.GunzipIfGZipped;
import org.drpowell.varitas.CLIRunnable;
import org.drpowell.varitas.Main;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFUtils;
import org.drpowell.vcf.VCFVariant;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

public class ApplyVCFFilters implements VCFIterator, CLIRunnable {
	private VCFIterator variants;
	
	@Argument(alias = "f", description = "script file(s) by which to filter variants, delimited by commas", delimiter = ",")
	private String[] filters;
	
	@Argument(alias = "j", description = "javascript to apply to each variant as a filter (if result is true, the variant passes)")
	private String javascriptFilter;
	
	@Argument(alias = "i", description = "input file of variants (VCF format)")
	private String input;
	
	@Argument(alias = "o", description = "output (.vcf) file (if neither -o or -x are provided, will write to stdout)")
	private String output;
	
	@Argument(alias = "x", description = "output (.xls) file")
	private String xls;
	
	@Argument(alias = "b", description = "apply biallelic filter")
	private static Boolean applyBiallelic = false;
	
	@Argument(alias = "m", description = "apply mendelian constraint filter")
	private static Boolean applyMendelianConstraint = false;
	
	@Argument(alias = "a", description = "file with additional headers to add to input vcf file")
	private String additionalHeaders;

	protected ApplyVCFFilters initialize(VCFIterator variants) {
		this.variants = variants;
		VCFHeaders vcfHeaders = variants.getHeaders();
		
		// WARNING-- this modifies the headers in the passed VCFIterator variants...
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
			}
		}
		if (applyMendelianConstraint) {
			variants = new MendelianConstraintFilter(variants);
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
			PrintWriter outWriter = null;

			if (output != null) {
				outWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(output), 1024));
			} else if (xls == null) { // both output and xls null -- write vcf output to stdout
				outWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024));
			}

			if (xls != null) {
				OutputStream os = new BufferedOutputStream(new FileOutputStream(xls), 1024);
				variants = new XLifyVcf(variants, os);
			}

			if (outWriter != null) {
				outWriter.print(variants.getHeaders());
				outWriter.println(variants.getHeaders().getColumnHeaderLine());
				while (variants.hasNext()) {
					outWriter.println(variants.next());
				}
				outWriter.close();
			} else {
				// need to loop through all of the variants anyway, for XLifyVcf
				while (variants.hasNext()) {
					variants.next();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			Args.usage(this);
		}
	}

	@Override
	public boolean hasNext() {
		return variants.hasNext();
	}

	@Override
	public VCFVariant next() {
		return variants.next();
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public VCFHeaders getHeaders() {
		return variants.getHeaders();
	}

}
