#!/usr/bin/perl -w
#
# Simple script to bulk-load test data into FOLIO
#
#
# Options
#  -u --okapiurl  URL.  Defaults to http://localhost:9130
#  -p --path path. Obligatory. For example /_/tenants
#  -t --tenant  tenant. Defaults to 'testlib'
#
# TODO: Take login arguments, do a login, get a JWT, and pass it along


use Getopt::Long qw(GetOptions);
use Data::Dumper;
use LWP::UserAgent;  # libwww-perl

# Parse arguments
my $okapiurl = "http://localhost:9130";
my $tenant = "testlib";
my $path = "";
GetOptions('okapiurl|u=s' => \$okapiurl,
           'tenant|t=s' => \$tenant,
           'path|p=s' => \$path)
  || die "$0 usage: [-u okapiurl] -p path [-t tenantid] \n";
if ( ! $path ) {
  die ("--path (or -p) must be specified. For example /_/tenants\n" );
}
$okapiurl =~ s"/+$""; # remove trailing slash, if any
$okapiurl .= $path;

print "Posting stuff to '$okapiurl' using tenant '$tenant' \n";
print print Dumper(\@ARGV), "\n";

my $ua = new LWP::UserAgent;
while (<>) {
  my $line = $_;
  chomp($line);
  print "About to post $line\n";
  my $resp = $ua->post($okapiurl,
    "X-Okapi-Tenant" => $tenant,
    "Content-Type" => "application/json",
    "Content" => $line );
  print "Got response :" . Dumper($resp) . "\n";
  if (! $resp->is_success) {
    print STDERR $resp->status_line, "\n";
    print STDERR $resp->decoded_content, "\n";
    die();
  }
}
