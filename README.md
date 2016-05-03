# Introduction
Analysis of genotypes and gene expression phenotypes (eQTLs) using ADAM and Spark

This demonstration program performs linear regression of genotypes against gene expression phenotypes (eQTL analysis), making use of ADAM Parquet Genotype file format as input, and Spark to perform computation which scales easily on cloud compute clusters.

Technologies used here include:
* [ADAM](https://github.com/bigdatagenomics/adam) from [Big Data Genomics](http://bdgenomics.org)
* [Apache Spark](https://spark.apache.org/): a fast engine for large-scale data processing
* [Apache Commons Math](http://commons.apache.org/proper/commons-math/)  java package
* Amazon AWS EC2 
*  [Presentation](https://docs.google.com/presentation/d/1F2jEplq6n36GJNCK_fgrloY3Fi9ujVl1x-sAPUg_DYU/edit#slide=id.g10df2eff66_2_20)


# Background
In genetics, statistical association tests are performed between genotype and phenotype.  In the case of a continuous trait such as height, linear regression can be used to associate the dosage (zero, one, or two copies) of an alternative allele at a position in the genome with a quantitative trait.  

Over the past decade it has become possible to measure simultaneously thousands of molecular gene expression phenotypes, the amount of a gene transcript made by a cells derived from a specific person, and to associate this molecular phenotype with genetic variation within a population of samples. These studies are often termed (eQTL) for expression Quantitative Trait Loci studies.

In this demonstration program, genotypes are input from the efficient ADAM genotype format which uses Parquet, and then Spark is used to perform billions of linear regression tests.  

For example, in the a test described below, 450,000 variants from chr2 having an alternative allele frequency between 10 and 90% in the European population are analyzed against 5000 gene expression phenotypes in a population of 54 individuals. Thus, there is a need to perform 450,000 * 5000 = 2.25 billion statistical tests.  

As discussed in the note at the end, the multiple testing problem in interpreting the output of these tests is another area where Spark could be applied.


# Source Data and References

Datasets analyzed here are derived from: (http://jungle.unige.ch/rnaseq_CEU60/)

Described in the paper: (http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3836232/)

#Building

To build from source first [download and install Maven](http://maven.apache.org/download.cgi).
Then at the command line in this cloned repository type:
```
mvn clean package
```

This will produce the JAR file target/uberScalajar-Spark_eQTL_assoc-0.1-SNAPSHOT.jar

#Running
Download Spark version 1.2.0 (http://spark.apache.org/downloads.html) and unpack it on your machine.

To run a self contained example using  data in the data/ directory, execute this command from the base of this clone repository, to run the jar using spark-submit:

```
  ./YOUR_SPARK_HOME/bin/spark-submit   \
     --class "org.justinpaschall.EQTLAssoc" \
     --master local[*]   \
     --driver-memory 4G \
     --executor-memory 4G \
      target/uberScalajar-Spark_eQTL_assoc-0.1-SNAPSHOT.jar data/probes100 \
       data/chr22vcf.adam 8 output_analysis_chr22_probes100
```

This job runs for 114 seconds on my machine.  Output is found in directory *output_analysis_chr22_probes100* divided into partition files written by Spark.  Output data is text in the form of tuples:

```
(chr_pos_allele, geneExpressionProbeName, Pvalue, Rsquared)
```

The four command line parameters following the jar, defined by the order they appear are:
```
<listOfProbes> <vcfdata> <numOfSparkPartitions> <outputDirName>
```

### Deploying on AWS EC2 and Scalability Metrics

Code and data were deployed on AWS EC2, and scaling properties tested using

* 32 executors with 32 Spark partitions ( 16 m3.large - 32 cores )
* 16 executors with 16 Spark partitions ( 8 m3.large - 16 cores )
* 8 executors with 8 Spark partitions ( 4 m3.large - 8 cores )
* Local machine with 8 Spark partitions ( 1 quadcore HT )

AWS EC2 clusters was launched using the spark_ec2 script found at YOUR_SPARK_HOME/ec2/spark-ec2
described at: (http://spark.apache.org/docs/1.2.1/programming-guide.html#deploying-to-a-cluster)

```
./spark-ec2 --key-pair myekeypairname --identity-file mkeypairname.pem  --region=us-east-1 --zone=us-east-1b --hadoop-major-version 2   --spark-version=1.2.0 -s 4 --instance-type=m3.large  launch myclustername1
```

Only the genotype file such as chr2vcf.adam should be copied to ephemeral-hdfs, the others remain local to driver

Note, due to a Python codec error regarding UTF8 when running spark_ec2.py on my Ubuntu machine, it was necessary to add the following code to spark_ec.py after the import statements at the beginning of the script:

```
reload(sys)  
sys.setdefaultencoding('utf8')
```

#####Results from AWS scalability testing

#####Linear regression against 100, 1000, 5000, 10000 gene expression phenotypes

######Analyzing ~77,000 variants from chromosome 22  (*Times in Minutes*)
|             | 32 cores | 16 cores | 8 cores | local - quadcore HT  |
| ----------- | -------- |:--------:|:-------:|:---------------------:|
| 100 pheno   | 1.5      | 1.92     | 1.96    | 2.02                  |
| 1000 pheno  | 3.9      | 5.7      | 10.4    | 14.1                  |
| 5000 pheno  | 13.9     | 23.29    | 47.1    | pending               |
| 10000 pheno | 27.7     | 47       | 94      | 135.1                 |

Analysis time scales linearly with addition of Spark executors (cores).  There is a constant cost to load or sort genotypes at beginning that needs to be analyzed with the addition of more samples.

######Analyzing ~450,000 variants from chromosome 2  (*Times in Minutes*)

|             | 32 cores | 16 cores | 8 cores | local - quadcore HT  |
| ----------- | -------- |:--------:|:---------:|:---------------------:|
| 100 pheno   | 2.7      | 4.0      | 8.32      | 15.5                  |
| 1000 pheno  | 15.7     | 29.2     | 55.1      | 74.9                  |
| 5000 pheno  | 76.7     | 134.3    | failed*   | not tried             |
| 10000 pheno | failed*  | not tried| not tried | not tried             |

As expected, the computation scales linearly with number of variants, numbers in the second table above are approx (478,000/78,000) = 5.5 times those in above table

*runs failed repeatedly, terminated leaving only _temporary marker, suspiciously both ended at 2.2 hours.  Requires further investigation, machines appear to have plenty of total memory unused near time of failure.

### Comparison with PLINK

[PLINK](http://pngu.mgh.harvard.edu/~purcell/plink/) is a standard tool used by genetics researchers, implementing a vast number of statistical tests.

PLINK's linear regression test was used here as benchmark for comparison, using the parameters:
```
plink -tfile mystudy --out run_chr22_10000_pheno  --linear --pheno out1_10000_pheno --all-pheno --noweb --pfilter 1e-4  
```

The VCF input file was converted to PLINK format using (VCFtools)[https://vcftools.github.io/index.html] with command:
```
vcftools --vcf myVCFfile.vcf --plink-tped
```

Spot checking p-value results indicate agreement within rounding error between results generated in this project and PLINK, though further comparison is needed.

Using the same test set of 10000 phenotypes and 77000 chromosome 22 variants above, a single instance of PLINK finishes in 320 minutes.  

PLINK is a single threaded application.   Scaled to 8 independent threads, as the PLINK job can be split by batches of variants, 8 independent threads running PLINK would finish this task in 40 minutes compared to 94 minutes using 8 cores on AWS EC2 in test above. 

PLINK is thus 2.35 times faster per thread, not surprising given its implementation in C. However using PLINK in parallel on a cluster could require significant work to launch and manage jobs, recover from error, and recombine results, exactly what Spark does for us.   


###To do: 
* Increase the number of samples to assess scaling properties of the initial groupBy phase
* Determine the cause of failure seen above at chr2 10000 pheno 32 core, and chr 22 5000 pheno 16 core 
* Implement further statistical tests beyond linear regression, motivated by those in PLINK and other tools 
* Empirical and machine learning methods to assess significance / reduce search space as described below


##### Note on statistical signifcance
This project demonstrates a way to efficiently and scalably perform parallel statistical tests using Spark, however scientific interpretation requires adjustment for the billions of tests performed.  Corrections could be based on prior hypotheses: for example that variants often affect expression in genes in the same region (cis-acting) or that variants in genes in known regulatory networks may interact.  Machine-learning based network analysis has been applied to this problem in the past and may be fruitful area for exploration with Spark. 

#Credits
Inspirations, including structure of maven project from:
(https://github.com/nfergu/popstrat)

Keep watching the exciting work of [Big Data Genomics](http://bdgenomics.org)

