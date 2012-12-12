package org.drpowell.xlifyvcf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.drpowell.varitas.CLIRunnable;
import org.drpowell.varitas.CompoundMutationFilter;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFParser;
import org.drpowell.vcf.VCFVariant;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

public class XLifyVcf implements CLIRunnable {
	public final Workbook workbook;
	private VCFParser vcfParser;
	private final CreationHelper createHelper;
	private Map<String, VCFMeta> infos;
	private Map<String, VCFMeta> formats;
	private List<String> samples;
	private String [] headers;
	private Sheet dataSheet;
	private int rowNum = 0;
	private BitSet numericColumns;
	private CellStyle hlink_style;
	private Map<Integer, HyperlinkColumn> columnsToHyperlink;
	private boolean applyBiallelicFilter;
	private static final int COLUMNS_TO_AUTO_RESIZE[] = {0, 1, 2, 9, 10, 11}; // FIXME- should index as string, or be configurable
	private static final int COLUMNS_TO_HIDE[] = {7, 8};
	private Map<String, String> headerComments;
	
	@Argument(alias = "f", description = "script file(s) by which to filter variants, delimited by commas", delimiter = ",")
	private String[] filters;
	
	@Argument(alias = "i", description = "input file of variants (VCF format)")
	private String input;
	
	@Argument(alias = "o", description = "output (.xls) file")
	private String output;
	
	private enum HyperlinkColumn {
		GENE("http://www.ncbi.nlm.nih.gov/gene?term=%s"),
		SNP("http://www.ncbi.nlm.nih.gov/projects/SNP/snp_ref.cgi?rs=%s"),
		OMIM("http://omim.org/search?search=%s"),
		GENETESTS("http://www.ncbi.nlm.nih.gov/sites/GeneTests/review/gene/%s?db=genetests&search_param=begins_with");
		
		public final String url;
		HyperlinkColumn(String url) { this.url = url; };
	}
	
	public XLifyVcf() {
		workbook = new HSSFWorkbook();
		createHelper = workbook.getCreationHelper();
	}
	
	protected XLifyVcf initialize(VCFParser parser) {
		vcfParser = parser;
		VCFHeaders vcfHeaders = parser.getHeaders();
		formats = vcfParser.getHeaders().formats();
		// TODO - fix this kludge in adding filter for biallelic mutations within genes
		if (vcfParser.getHeaders().getSamples().size() == 3) {
			applyBiallelicFilter = true;
			vcfHeaders.add(VCFParser.parseVCFMeta("##INFO=<ID=BIALLELIC,Number=0,Type=Flag,Description=\"variant participates in biallelic non-reference inheritance in a gene\">"));
		} else {
			applyBiallelicFilter = false;
		}
		infos = vcfParser.getHeaders().infos();
		samples = vcfHeaders.getSamples();
		headers = makeHeaders();
		makeMetaSheet();
		dataSheet = setupDataSheet();
		return this;
	}
	
	private String [] makeHeaders() {
		numericColumns = new BitSet();
		ArrayList<String> out = new ArrayList<String>(Arrays.asList(vcfParser.getHeaders().getColumnHeaderLine().split("\t", -1)));
		numericColumns.set(1);
		numericColumns.set(5);
		headerComments = new HashMap<String, String>();
		// TODO - add comments to the header fields describing them (from VCFMeta Description)
		for (VCFMeta m: infos.values()) {
			if ("1".equals(m.getValue("Number"))) {
				String type = m.getValue("Type");
				if ("Integer".equals(type) || "Float".equals(type)) {
					numericColumns.set(out.size());
				}
			}
			headerComments.put(m.getValue("ID"), m.getValue("Description"));
			out.add(m.getId());
		}
		for (String s : samples) {
			for (VCFMeta m: formats.values()) {
				if ("1".equals(m.getValue("Number"))) {
					String type = m.getValue("Type");
					if ("Integer".equals(type) || "Float".equals(type)) {
						numericColumns.set(out.size());
					}
				}
				out.add(s + "_" + m.getId());
			}
		}
		return out.toArray(new String[out.size()]);
	}

	private void mapHeadersToHyperlinks() {
		Map<String, HyperlinkColumn> acceptableHeaders = new HashMap<String, HyperlinkColumn>();
		columnsToHyperlink = new HashMap<Integer, HyperlinkColumn>(16);
		acceptableHeaders.put("gene", HyperlinkColumn.GENE);
		acceptableHeaders.put("gene_name", HyperlinkColumn.GENE);
		acceptableHeaders.put("id", HyperlinkColumn.SNP);
		acceptableHeaders.put("in_omim", HyperlinkColumn.OMIM);
		acceptableHeaders.put("omim", HyperlinkColumn.OMIM);
		acceptableHeaders.put("in_genetests", HyperlinkColumn.GENETESTS);
		acceptableHeaders.put("genetests", HyperlinkColumn.GENETESTS);
		for (int i = 0; i < headers.length; i++) {
			HyperlinkColumn hc = acceptableHeaders.get(headers[i].toLowerCase());
			if (hc != null) { columnsToHyperlink.put(i, hc); }
		}

	}
	
	private Sheet setupDataSheet() {
		Sheet data = workbook.createSheet("data");
		Drawing drawing = data.createDrawingPatriarch();
		workbook.setActiveSheet(workbook.getSheetIndex(data));
		data.createFreezePane(5, 1);
		Row r = data.createRow(rowNum);
		for (int c = 0; c < headers.length; c++) {
			Cell cell = r.createCell(c);
			cell.setCellValue(headers[c]);
			if (headerComments.containsKey(headers[c])) {
				ClientAnchor anchor = createHelper.createClientAnchor();
				anchor.setCol1(cell.getColumnIndex());
				anchor.setCol2(cell.getColumnIndex()+3);
				anchor.setRow1(cell.getRowIndex());
				anchor.setRow2(cell.getRowIndex()+1);
				Comment comment = drawing.createCellComment(anchor);
				comment.setString(createHelper.createRichTextString(headerComments.get(headers[c])));
				cell.setCellComment(comment);
			}
		}
		
		hlink_style = workbook.createCellStyle();
		Font hlink_font = workbook.createFont();
	    hlink_font.setUnderline(Font.U_SINGLE);
	    hlink_font.setColor(IndexedColors.BLUE.getIndex());
	    hlink_style.setFont(hlink_font);
	    
	    mapHeadersToHyperlinks();
	    
	    return data;
	}
	
	private void makeMetaSheet() {
		Sheet metaSheet = workbook.createSheet("metadata");
		List<VCFMeta> headers = vcfParser.getHeaders();
		for (int i = 0; i < headers.size(); i++) {
			metaSheet.createRow(i).createCell(0).setCellValue(headers.get(i).toString());
		}
	}
	
	private void writeRow(VCFVariant v) {
		rowNum++;
		ArrayList<String> data = new ArrayList<String>(headers.length);
		Row r = dataSheet.createRow(rowNum);
		data.addAll(Arrays.asList(v.toString().split("\t", -1)));
		for (String i : vcfParser.getHeaders().infos().keySet()) {
			data.add(v.getInfoField(i));
		}
		String [] calls = v.getCalls();
		String [] callFormat = v.getFormat().split(":", -1);
		Map<String, Integer> formatIndices = new HashMap<String, Integer>(callFormat.length * 2);
		for (int i = 0; i < callFormat.length; i++) {
			formatIndices.put(callFormat[i], i);
		}
		if (calls.length == samples.size()) {
			for (String call: calls) {
				String [] subfields = call.split(":");
				for (String k: formats.keySet()) {
					Integer i = formatIndices.get(k);
					if (i == null || i >= subfields.length) {
						data.add("");
					} else {
						data.add(subfields[i]);
					}
				}
			}
		} else {
			throw new RuntimeException("Problem with VCF line: " + v.toString());
		}
		for (int i = 0; i < data.size(); i++) {
			String d = data.get(i);
			if (d != null && !".".equals(d)) {
				Cell c = r.createCell(i);
				if (numericColumns.get(i)) {
					try {
						Double db = new Double(d);
						c.setCellValue(db.doubleValue());
					} catch (NumberFormatException nfe) {
						// FIXME - maybe I should log this, maybe not
						c.setCellValue(d);
					}
				} else {
					c.setCellValue(d);
				}
			}
		}
		makeHyperlinks(r);
	}
	
	private void makeHyperlinks(Row r) {
		DataFormatter df = new DataFormatter();
		Cell chromosomeCell = r.getCell(0);
		String chr = df.formatCellValue(chromosomeCell);
		long pos = Math.round(r.getCell(1).getNumericCellValue());
		Hyperlink link = createHelper.createHyperlink(Hyperlink.LINK_URL);
		link.setAddress(String.format("http://genome.ucsc.edu/cgi-bin/hgTracks?db=hg19&position=chr%s:%d-%d",
				chr, pos-100, pos+100));
		chromosomeCell.setHyperlink(link);
		chromosomeCell.setCellStyle(hlink_style);
		
		for (Map.Entry<Integer, HyperlinkColumn> kv : columnsToHyperlink.entrySet()) {
			Cell c = r.getCell(kv.getKey());
			if (c != null) {
				link = createHelper.createHyperlink(Hyperlink.LINK_URL);
				link.setAddress(String.format(kv.getValue().url, df.formatCellValue(c)));
				c.setHyperlink(link);
				c.setCellStyle(hlink_style);
			}
		}
	}
	
	public void doWork() {
		Iterator<VCFVariant> variants = vcfParser.iterator();
		if (filters != null) {
			for (String filter : filters) {
				Reader r = new InputStreamReader(ClassLoader.getSystemResourceAsStream(filter));
				variants = new ScriptVCFFilter(variants, r);
			}
		}
		//Iterator<VCFVariant> variants = new DefaultVCFFilter(vcfParser.iterator());
		if (applyBiallelicFilter) {
			variants = new CompoundMutationFilter(variants, vcfParser.getHeaders());
		}
		while (variants.hasNext()) {
			writeRow(variants.next());
			// TODO: progress
		}
		for (int i = 0; i < COLUMNS_TO_AUTO_RESIZE.length; i++) {
			dataSheet.autoSizeColumn(COLUMNS_TO_AUTO_RESIZE[i]);
		}
		for (int i = 0; i < COLUMNS_TO_HIDE.length; i++) {
			dataSheet.setColumnHidden(COLUMNS_TO_HIDE[i], true);
		}
	}
	
	public void writeOutput(OutputStream out) throws IOException {
		workbook.write(out);
	}
	
	public void doMain(List<String> extraArgs) {
		if (input == null && !extraArgs.isEmpty()) {
			input = extraArgs.remove(0);
		}
		if (input == null) {
			input = "-";
		}
		if (output == null && !extraArgs.isEmpty()) {
			output = extraArgs.remove(0);
		}
		if (input == null || output == null) {
			Args.usage(this);
			System.exit(1);
		}
		try {
			BufferedReader reader;
			if ("-".equals(input)) {
				reader = new BufferedReader(new InputStreamReader(System.in));
			}
			if (input.endsWith(".gz")) {
				reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(input))));
			} else {
				reader = new BufferedReader(new FileReader(input));
			}
			initialize(new VCFParser(reader)).doWork();
			OutputStream os = new FileOutputStream(output);
			writeOutput(os);
			os.close();
		} catch (Exception e) {
			System.err.println(e);
			Args.usage(this);
		}
	}

}
