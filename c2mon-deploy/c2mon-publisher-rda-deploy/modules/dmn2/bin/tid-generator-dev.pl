#!/usr/bin/perl
use lib '/user/alaser/perllib/Config-Properties-1.71/blib/lib';
use CGI qw(:standard);
use CGI::Carp qw(warningsToBrowser fatalsToBrowser);
use Config::Properties;
use DBD::Oracle;
use File::Path;
use Cwd 'abs_path';

##
# Definition of global variables
##

#find out the name of the home folder of the application
#home folder is: bin/../

my $cdir =  abs_path($0);

my @pathtokens = split(/\//,$cdir);

# we are in bin folder, so the home folder is one level up
my $appdir = @pathtokens[scalar(@pathtokens)-3];

my $basedir = "/opt/dmn2-publisher-rda/conf";

my $c2monClientPropertiesFile= "/user/dmndev/c2mon/client/client.properties";

# Reading property file client.properties
open PROPS, "< $c2monClientPropertiesFile"
  or die "Unable to open configuration file $c2monClientPropertiesFile";

my $c2monProperties = new Config::Properties();

$c2monProperties->load(*PROPS);

# Separate database connection URL is necessary for the perl DBI connector

my $dbiUser = $c2monProperties->getProperty("c2mon.jdbc.config.user");
my $dbiPassword = $c2monProperties->getProperty("c2mon.jdbc.config.password");

# get the url (in java jdbc format)
my $dbiUrl = $c2monProperties->getProperty("c2mon.jdbc.config.url");
# change it to perl dbi format
$dbiUrl =~ s/jdbc:oracle:thin:@/dbi:Oracle:/g;


my $dbh = DBI->connect( $dbiUrl, $dbiUser, $dbiPassword )
  || die( $DBI::errstr . "\n" );


my $fetch_equipments_sql = <<END;
select METRIC_DATA_TAG_ID from DMN_METRICS_V where rda_publish_flag = 'Y'
END

my $sth = $dbh->prepare("${fetch_equipments_sql}")	
	  || die "Couldn't prepare statement: " . $dbh->errstr;
my @data;
$sth->execute()
    || die "Couldn't execute statement: " . $sth->errstr;

open( MYFILE,
" > ${basedir}/publisher-new.tid"
);

while ( @data = $sth->fetchrow_array() ) {
	my $tag_id = $data[0];

  print MYFILE "${tag_id}\n";

}

if ( $sth->rows == 0 ) {
  # print "No tags are defined to be published to RDA.\n\n";
}
	
$sth->finish;
close(MYFILE);