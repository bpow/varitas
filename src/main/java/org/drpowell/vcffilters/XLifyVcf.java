package org.drpowell.vcffilters;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

/**
 * A file that wraps a VCFIterator to write the variants to a .XLS file.
 * 
 * XLifyVcf itself implements VCFIterator, so it can be used as part of a chain of filters.

 * The constructor is given a VCFIterator to wrap, and an OutputStream to which the output should be written.
 * As each variant is processed (with each call of this.next()), the internal representation of the .XLS
 * file is made. When the client iterator is depleted (when hasNext() returns false), the file is written.
 * Because of this behavior, it is important to process through all of the values of the iterator if you
 * actually want to write a file.
 * 
 * @author bpow
 *
 */
public class XLifyVcf implements VariantOutput {
	public final Workbook workbook;
	private final VCFIterator variants;
	private final OutputStream os;
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
	private static final int COLUMNS_TO_AUTO_RESIZE[] = {0, 1, 2, 9, 10, 11}; // FIXME- should index as string, or be configurable
	private static final String[] COLUMNS_TO_HIDE = "FILTER INFO FORMAT AC AC1 AF AF1 AN CGT UGT CLR FQ G3 HWE INDEL IS PC2 PCHI2 PR QCHI2 VDB".split(" ");
	private Map<String, String> headerComments;

	// this is really hacky-- most VCF files tend to have the FORMAT header lines in alphabetical
	// order, which is really obnoxious for viewing. I like this order better...
	private static final String[] PREFERRED_FORMAT_ORDER = {
		"GT", "AD", "DP", "DV", "RR", "VR", "GQ", "PL", "GL"
	};
		
	private enum HyperlinkColumn {
		GENE("http://www.ncbi.nlm.nih.gov/gene?term=%s"),
		SNP("http://www.ncbi.nlm.nih.gov/projects/SNP/snp_ref.cgi?rs=%s"),
		OMIM("http://omim.org/search?search=%s"),
		GENETESTS("http://www.ncbi.nlm.nih.gov/sites/GeneTests/review/gene/%s?db=genetests&search_param=begins_with");
		
		public final String url;
		HyperlinkColumn(String url) { this.url = url; };
	}
	
	/**
	 * Wrap the variants VCFIterator, planning to write output to the specified OutputStream
	 * 
	 * @param variants
	 * @param os
	 */
	public XLifyVcf(VCFIterator variants, OutputStream os) {
		this.variants = variants;
		this.os = os;
		workbook = new HSSFWorkbook();
		createHelper = workbook.getCreationHelper();
		VCFHeaders vcfHeaders = variants.getHeaders();
		Map<String, VCFMeta> headerFormats = vcfHeaders.formats();
		// make the formats LinkedHashMap in a special order
		formats = new LinkedHashMap<String, VCFMeta>(headerFormats.size()*3/2, 0.75f);
		for (String fkey : PREFERRED_FORMAT_ORDER) {
			VCFMeta tmpMeta;
			if ((tmpMeta = headerFormats.get(fkey)) != null) {
				formats.put(fkey, tmpMeta);
			}
		}
		formats.putAll(headerFormats);
		infos = vcfHeaders.infos();
		samples = vcfHeaders.getSamples();
		headers = makeHeaders();
		makeMetaSheet();
		dataSheet = setupDataSheet();
	}
	
	private String [] makeHeaders() {
		numericColumns = new BitSet();
		ArrayList<String> out = new ArrayList<String>(Arrays.asList(variants.getHeaders().getColumnHeaderLine().split("\t", -1)));
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
				anchor.setCol2(cell.getColumnIndex()+5);
				anchor.setRow1(cell.getRowIndex());
				anchor.setRow2(cell.getRowIndex()+10);
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
		List<VCFMeta> headers = variants.getHeaders();
		for (int i = 0; i < headers.size(); i++) {
			metaSheet.createRow(i).createCell(0).setCellValue(headers.get(i).toString());
		}
	}
	
	private VCFVariant writeRow(VCFVariant v) {
		rowNum++;
		ArrayList<String> data = new ArrayList<String>(headers.length);
		Row r = dataSheet.createRow(rowNum);
		data.addAll(Arrays.asList(v.toString().split("\t", -1)));
		for (String i : variants.getHeaders().infos().keySet()) {
			String value = v.getInfoValue(i, true);
			if ("".equals(value)) {
				value = i; // flag fields should display as something.
			}
			data.add(value);
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
			if (d != null && !".".equals(d) && !"".equals(d)) {
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
		return v;
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
	
	private void writeOutput() throws IOException {
		// FIXME - could consider making this public (would need to allow writing only once)
		for (int i = 0; i < COLUMNS_TO_AUTO_RESIZE.length; i++) {
			dataSheet.autoSizeColumn(COLUMNS_TO_AUTO_RESIZE[i]);
		}
		List<String> headerList = Arrays.asList(headers);
		for (String hideCol : COLUMNS_TO_HIDE) {
			int i = headerList.indexOf(hideCol);
			if (i>=0) dataSheet.setColumnHidden(i, true);
		}
		workbook.write(os);
		os.close();
	}

	@Override
	public boolean hasNext() {
		boolean hasNext = variants.hasNext();
		if (!hasNext) {
			try {
				writeOutput();
			} catch (IOException e) {
				throw new RuntimeException("Problem writing excel file from VCF variants", e);
			}
		}
		return hasNext;
	}

	@Override
	public VCFVariant next() {
		return writeRow(variants.next());
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
