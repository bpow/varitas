addSnpEffSplitter()
addGeneAnnotator("in_omim", "data/omimgenes")
addGeneAnnotator("in_hgmd", "data/hgmdgenes")
addGeneAnnotator("in_cosmic", "data/cosmicgenes")
addGeneAnnotator("in_genetests", "data/genetestsgenes")
addGeneAnnotator("exclusion", "data/fajardogenes")
addGeneAnnotator("hgnclinks", "data/hgnc.txt").hasHeader(true).setKeyColumn(2).
    setOutputColumns("3=HGNC_NAME")
addVCFAnnotator("data/niehs95.snps.vcf.gz", "AF=NIEHSAF")
addVCFAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")
addVCFAnnotator("data/ncbiSNP137.vcf.gz", 
    "SCS,GMAF,PM,G5A,G5").setCopyID(true)
addVCFAnnotator("data/ESP6500SI.vcf.gz",
    "MAF=ESPMAF,GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
addVCFAnnotator("data/Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz",
    "AF=CG54AF").setAddChr(true).setRequirePass(true)
addTSVAnnotator("data/dbNSFP2.0b4_variant.gz",
    "22=SIFT,23=HDIV_SCORE,24=HDIV_PRED,25=HVAR_SCORE,26=HVAR_PRED," +
    "27=LRT_SCORE,28=LRT_PRED,29=MT_SCORE,30=MT_PRED,31=MA_SCORE,32=MA_PRED," +
    "33=GERP_NR,34=GERP_RS,35=PHYLOP,38=LRT_OMEGA").useHeader(true).checkRef(3).checkAlt(4)
