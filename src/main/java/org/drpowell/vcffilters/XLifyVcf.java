package org.drpowell.vcffilters;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.drpowell.vcf.VariantContextIterator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A file that wraps a VariantContextIterator to write the variants to a .XLS file.
 * 
 * XLifyVcf itself implements VariantContextIterator, so it can be used as part of a chain of filters.

 * The constructor is given a VariantContextIterator to wrap, and an OutputStream to which the output should be written.
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
	private final VariantContextIterator variants;
	private final OutputStream os;
	private final CreationHelper createHelper;
	private Map<String, VCFInfoHeaderLine> infos;
	private Map<String, VCFFormatHeaderLine> formats;
	private List<String> samples;
	private String [] headers;
	private Sheet dataSheet;
	private int rowNum = 0;
	private BitSet numericColumns;
	private CellStyle hlink_style;
	private CellStyle wrapped_style;
	private CellStyle header_style;
	private Map<Integer, HyperlinkColumn> columnsToHyperlink;
	private static final String[] COLUMNS_TO_HIDE = "FILTER INFO FORMAT AC AC1 AF AF1 AN CGT UGT CLR FQ G3 HWE INDEL IS PC2 PCHI2 PR QCHI2 VDB".split(" ");
	private Map<String, String> headerComments;
	private static final boolean SPLIT_INFO_LISTS_ACROSS_LINES = true;

	// this is really hacky-- most VCF files tend to have the FORMAT header lines in alphabetical
	// order, which is really obnoxious for viewing. I like this order better...
	private static final String[] PREFERRED_FORMAT_ORDER = {
		"GT", "AD", "DP", "DV", "RR", "VR", "GQ", "PL", "GL"
	};

	@Override
	public void close() {
		// FIXME -- what to do here?
	}

	private enum HyperlinkColumn {
		GENE("http://www.ncbi.nlm.nih.gov/gene?term=%s"),
		SNP("http://www.ncbi.nlm.nih.gov/projects/SNP/snp_ref.cgi?rs=%s"),
		OMIM("http://omim.org/search?search=%s"),
		GENETESTS("http://www.ncbi.nlm.nih.gov/sites/GeneTests/review/gene/%s?db=genetests&search_param=begins_with");
		
		public final String url;
		HyperlinkColumn(String url) { this.url = url; };
	}
	
	/**
	 * Wrap the variants VariantContextIterator, planning to write output to the specified OutputStream
	 * 
	 * @param variants
	 * @param os
	 */
	public XLifyVcf(VariantContextIterator variants, OutputStream os) {
		this.variants = variants;
		this.os = os;
		workbook = new XSSFWorkbook();
		createHelper = workbook.getCreationHelper();
		VCFHeader vcfHeader = variants.getHeader();
		Collection<VCFFormatHeaderLine> headerFormats = vcfHeader.getFormatHeaderLines();
		Set<String> headerNamesInFile = headerFormats.stream().map(h -> h.getID()).collect(Collectors.toSet());
		// make the formats LinkedHashMap in a special order
		formats = new LinkedHashMap<String, VCFFormatHeaderLine>(headerFormats.size()*3/2, 0.75f);
		for (String fkey : PREFERRED_FORMAT_ORDER) {
			VCFFormatHeaderLine tmpLine;
			if ((tmpLine = vcfHeader.getFormatHeaderLine(fkey)) != null) {
				formats.put(fkey, tmpLine);
			}
		}
		for (VCFFormatHeaderLine tmpLine : headerFormats) {
			formats.put(tmpLine.getID(), tmpLine);
		}
		infos = new LinkedHashMap<String, VCFInfoHeaderLine>();
		for (VCFInfoHeaderLine infoLine : vcfHeader.getInfoHeaderLines()) {
			infos.put(infoLine.getID(), infoLine);
		}
		samples = vcfHeader.getSampleNamesInOrder();
		headers = makeHeaders();
		makeMetaSheet();
		dataSheet = setupDataSheet();
	}
	
	private String [] makeHeaders() {
		numericColumns = new BitSet();

		ArrayList<String> out = new ArrayList<String>(Arrays.asList("CHROM POS ID REF ALT QUAL FILTER INFO".split(" ")));
		out.addAll(variants.getHeader().getSampleNamesInOrder());
		numericColumns.set(1);
		numericColumns.set(5);
		headerComments = new HashMap<String, String>();
		if (infos.containsKey("Gene_name")) {
			out.add("Gene_name"); // FIXME -- this is sooo hacky-- but I want to get the gene name moved earlier!
		}
		for (String s : samples) {
			for (VCFFormatHeaderLine fhl: formats.values()) {
				if (1 == fhl.getCount()) {
					VCFHeaderLineType type = fhl.getType();
					if (type == VCFHeaderLineType.Integer || type == VCFHeaderLineType.Float) {
						numericColumns.set(out.size());
					}
				}
				out.add(s + "_" + fhl.getID());
			}
		}
		for (VCFInfoHeaderLine ihl: infos.values()) {
			if ("Gene_name".equals(ihl.getID())) {
				continue; // already added it at the beginning
			}
			if (ihl.getCount() == 1) {
				VCFHeaderLineType type = ihl.getType();
				if (type == VCFHeaderLineType.Integer || type == VCFHeaderLineType.Float) {
					numericColumns.set(out.size());
				}
			}
			headerComments.put(ihl.getID(), ihl.getDescription());
			out.add(ihl.getID());
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

		hlink_style = workbook.createCellStyle();
		Font hlink_font = workbook.createFont();
		hlink_font.setUnderline(Font.U_SINGLE);
		hlink_font.setColor(IndexedColors.BLUE.getIndex());
		hlink_style.setFont(hlink_font);
	    
		wrapped_style = workbook.createCellStyle();
		wrapped_style.setWrapText(true);
	    
		header_style = workbook.createCellStyle();
		header_style.setRotation((short) 60);
		header_style.setAlignment(CellStyle.ALIGN_LEFT);
	    
		Row r = data.createRow(rowNum);
		for (int c = 0; c < headers.length; c++) {
			Cell cell = r.createCell(c);
			cell.setCellValue(headers[c]);
			if (headerComments.containsKey(headers[c])) {
				ClientAnchor anchor = createHelper.createClientAnchor();
				anchor.setCol1(cell.getColumnIndex());
				anchor.setCol2(cell.getColumnIndex()+2);
				anchor.setRow1(cell.getRowIndex());
				anchor.setRow2(cell.getRowIndex()+1);
				Comment comment = drawing.createCellComment(anchor);
				comment.setString(createHelper.createRichTextString(headerComments.get(headers[c])));
				cell.setCellComment(comment);
			}
			// For some reason, row.setStyle is not working
			cell.setCellStyle(header_style);
		}
		
	    mapHeadersToHyperlinks();
	    
	    return data;
	}
	
	private void makeMetaSheet() {
		Sheet metaSheet = workbook.createSheet("metadata");
		int i = 0;
		for (VCFHeaderLine header : variants.getHeader().getMetaDataInInputOrder()) {
			metaSheet.createRow(i).createCell(0).setCellValue(header.toString());
			i++;
		}
	}
	
	private VariantContext writeRow(VariantContext v) {
		rowNum++;
		ArrayList<String> data = new ArrayList<String>(headers.length);
		Row r = dataSheet.createRow(rowNum);
		// 'fixed' columns
		data.addAll(Arrays.asList(v.toString().split("\t", -1)));
		
		// hacky special-case for 'Gene_name'
		if (infos.containsKey("Gene_name")) {
			data.add(v.getAttributeAsString("Gene_name", ""));
		}

		// genotype columns
		for (Genotype gt : v.getGenotypesOrderedByName()) {
			data.add(gt.getGenotypeString());
			for (String fkey : formats.keySet()) {
				Object val = gt.getAnyAttribute(fkey);
				data.add(val == null ? "" : val.toString());
			}
		}

		// 'INFO' columns
		int height = 1; // height in # of lines
		for (String i : infos.keySet()) {
			Object value = v.getAttribute(i);
			if (value == null) {
				value = "";
			}
			else if ("".equals(value)) {
				value = i; // flag fields should display as something.
			}
			else {
				String vString = value.toString();
				if (SPLIT_INFO_LISTS_ACROSS_LINES && vString.contains(",")) {
					height = Math.max(height, vString.length()-vString.replace(",", "").length()+1);
					value = vString.replace(",", ",\n");
				}
			}
			data.add(value.toString());
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
					if (SPLIT_INFO_LISTS_ACROSS_LINES && d.contains("\n")) {
						c.setCellStyle(wrapped_style);
					}
				}
			}
		}
		if (SPLIT_INFO_LISTS_ACROSS_LINES && height > 1) {
			r.setHeight((short) (dataSheet.getDefaultRowHeight() * height));
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
		List<String> headerList = Arrays.asList(headers);
		for (String hideCol : COLUMNS_TO_HIDE) {
			int i = headerList.indexOf(hideCol);
			if (i>=0) dataSheet.setColumnHidden(i, true);
		}
		
		for (int i = 0; i < headers.length; i++) {
			// do not bother resizing hidden columns-- saves some time
			if (!dataSheet.isColumnHidden(i)) dataSheet.autoSizeColumn(i);
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
	public VariantContext next() {
		return writeRow(variants.next());
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public VCFHeader getHeader() {
		return variants.getHeader();
	}

}
