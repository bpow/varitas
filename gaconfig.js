addVCFAnnotator("data/niehs95.indels.vcf.gz", "AF=NIEHSIAF")
addVCFAnnotator("data/niehs95.snps.vcf.gz", "AF=NIEHSAF")
addVCFAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")
addVCFAnnotator("data/ncbiSNP135.vcf.gz", 
    "SCS,GMAF,CLN,PM,G5A,G5").setCopyID(true)
addVCFAnnotator("data/ESP5400.vcf.gz",
    "MAF=ESPMAF,GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
addVCFAnnotator("data/Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz",
    "AF=CG54AF").setAddChr(true).setRequirePass(true)
addTSVAnnotator("data/dbNSFP1.3.gz",
    "17=PHYLOP,18=PHYLOP_PRED,19=SIFT,20=SIFT_PRED,21=PP2,22=PP2_PRED,23=LRT,24=LRT_PRED,25=MT,26=MT_PRED").checkRef(3).checkAlt(4)
