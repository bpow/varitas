VARITAS - Variant Annotation with Rapid Incorporation of Tabular Annotation Sources
=======================================================================================

VARITAS is designed to help people who are evaluating genome sequence variants.


Design Goals
------------

VARITAS aims to be:
* Flexible: Incorporate new data sources easily, change a text file rather
  than recompile.
* Portable: What if you prefer Linux, but your boss uses Macintoshes and
  everyone else in the lab has to use Windows because that is what IT
  provides them? VARITAS runs on the Java(tm) virtual machine, and can be
  installed as a single .jar file (but it needs data sources, of course...)
* (Reasonably) small: Reference data sets are compressed (using bgzip) to
  minimize storage space and disk IO. The references can be stored on the
  laptop you bring to lab meeting.
* (Reasonably) fast: Reference data sets are indexed using Heng Li's `tabix`
  R-tree based interval indexing. Some trade-offs for speed were made to
  maintain portability.

Compilation
-----------

A [gradle](http://gradle.org) build wrapper is provided. After code is checkout out from github, from within the working directory one can run:
    ./gradlew
(or `gradlew.bat` for windows systems). This will create an executatble .jar file as `build/libs/varitas.jar`. This jar file includes all of the library dependencies, so it can be copied elsewhere (or to a different computer) and still run fine.

Usage:
------

If you just run `java -jar varitas.jar`, you get a list of subcommands (currently just `Varitas` and `XLifyVCF`). Help for command options for subcommands can be obtained:

    java -jar Varitas -h

To annotate some variants:

    java -jar Varitas -c config.js -i input.vcf -o output.vcf

If no '-i' or '-o' options are given, then the standard input and standard output streams are used, so this works:

    zcat input.vcf.gz | java -jar Varitas -c config.js | bgzip -c > output.vcf.gz

Varitas config files:
---------------------

Since version 6.0, the standard Java runtime environment (JRE) comes with a javascript interpreter. Varitas uses this to process configuration files, allowing for flexibility in terms of annotation sources and their configuration. With JSR-233, this could easily be expanded to allow for configuration in other scripting languages (Jython, Groovy, etc...). The file `config.js` provides an example of configuration.

VCF-based, tab-delimited, or gene-based annotations are added with `addVCFAnnotator`, `addTSVAnnotator` and `addGeneAnnotator` respectively. Subsequent modifications/configuration of these annotators can be called as a 'chain'. All public methods in the Java classes for the annotators are available.

Data files will be looked for relative to:
1. The current directory
2. The location of the configuration file
3. The user's home directory (as defined by the operating system).

### addVCFAnnotator ###

This is the 'workhorse' annotator for varitas. Arguments to `addVCFAnnotator` are the filename of the database file and a comma-separated string regarding which portions of the `INFO` field of the VCF file to include in the output. Because there is no central source of VCF INFO keys, there can be a clash of keys from different data sources. Varitas allows for remapping of keys by specifying 'from=to' for an INFO key name.

For example, for Thousand Genomes project data:

    addVCFAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")

#### Additional (chained) configuration

* `setCopyID`: copy the ID field from the reference dataset to the
  ID field of the output. Example:

    addVCFAnnotator("data/ncbiSNP137.vcf.gz", "SCS,GMAF,PM,G5A,G5").setCopyID(true)

* `setRequirePass`: only annotate when the variant in the reference
  PASSed all filters in the VCF file

The AF key gets remapped to TGAF (Thousand Genomes Allele frequency).

### addTSVAnnotator ###

This is used for dbNSFP, and can also be used for custom data sources that you do not want to put into a VCF format. Arguments to `addTSVAnnotator` are the reference data file (bgzip-compressed and tabix indexed) and a comma-separated list of columns to include in the output. I configure dbNSFP as follows:

    addTSVAnnotator("data/dbNSFP2.0b3_variant.gz",
      "22=SIFT,23=HDIV_SCORE,24=HDIV_PRED,25=HVAR_SCORE,26=HVAR_PRED," +
      "27=LRT_SCORE,28=LRT_PRED,29=MT_SCORE,30=MT_PRED,31=GERP_NR,32=GERP_RS," +
      "33=PHYLOP,36=LRT_OMEGA").useHeader(true).checkRef(3).checkAlt(4)

For each column the column number (counted starting from 1) must be specified, followed by the '=' character, followed by the `INFO` key to be used for the output.

#### Additional (chained) configuration

* `checkRef` / `checkAlt`: specify a column for the reference
  and variant allele for a site, and only annotate at a site if
  the ref and alt alleles match in the query and reference files
* `useHeader`: use to indicate that the first line of the file is
  a header line. Varitas can use this line to give more information
  in the header of the output VCF file.

### addGeneAnnotator ###

Adds an annotation based on whether the VCF `INFO` field `Gene_name` in the input file matches a reference file. Arguments are:

1. A tag to be used as the output `INFO` key
2. The reference data file

A simple example:

    addGeneAnnotator("in_cosmic", "data/cosmicgenes")

The file `cosmicgenes` just contains a list of genes present in cosmic

#### Additional (chained) configuration

If the reference file is more than just a list of genes (if it is a tab-separated file) then more information can be included in the output:

    addGeneAnnotator("hgnclinks", "data/hgnc.txt").hasHeader(true).setKeyColumn(2).
      setOutputColumns("3=HGNC_NAME")

Database preparation:
---------------------

### Automatic ###

This part is not all that portable (yet). The `data` directory contains a `Makefile` which can be used to download and prepare some useful data sets. Requirements include `tabix`, `bgzip`, `zip`, `bzcat`, some standard GNU utilities, and of course `make`.

I have tested on Ubuntu and CentOS, but this should also work in other version of Linux and possibly MacOS X (provided the prerequisites are installed).

If all the prerequisites are installed, just move to the data directory and type:

    make

### Manual ###

Using information from a the info fields of a VCF file is easiest: the file just needs to be compressed and indexed:

    bgzip reference.vcf # makes the file reference.vcf.gz
    tabix -p vcf reference.vcf.gz
