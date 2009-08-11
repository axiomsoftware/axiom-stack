#!/usr/bin/perl
use Switch;

my $logfile=$ARGV[0];

my @requestA = ();
my @connectA = ();
my @connectedA = ();
my @matchA = ();
my @nomatchA = ();
my @r200A = ();
my @r400A = ();
my @r500A = ();

my $r200 = 0;
my $r400 = 0;
my $r500 = 0;
my $connected_max = 0;
my $connect_count = 0;
my $connect_mean_time = 0;
my $request_count = 0;
my $request_mean_time = 0;
my $match = 0;
my $nomatch = 0;

open logfile, "<$logfile" or die("could not open log file: $logfile!");
foreach $line (<logfile>) {
	chomp($line);
	if (! ($line =~ /^#/) ) {
		$line =~ s/^stats\: //g;
		@line_split = split(/ /,$line);
		switch($line){
			case (/^2[0-9][0-9]/) { 
				push(@r200A, $line); 
				$r200 = $line_split[2]; 
			}
			case (/^4[0-9][0-9]/) {  
				push(@r400A, $line); 
				$r400 = $line_split[2]; 
			}
			case (/^5[0-9][0-9]/) {  
				push(@r500A, $line); 
				$r500 = $line_split[2]; 
			}
			case (/^error_connect_econnrefused/) {  print "$line\n"; }
			case (/^connect /) {
				push(@connectA, $line); 
				$connect_count = $line_split[7];
				$connect_mean_time = $line_split[6];
			}
			case (/^connected/) {
				push(@connectedA, $line); 
				if ($line_split[2] > $connected_max){
					$connected_max = $line_split[2];
				}
			}
			case (/^match/) { $match = $line_split[2]; }
			case (/^nomatch/) { $nomatch = $line_split[2]; }
			case (/^request/) {
				push(@requestA, $line); 
				$request_count = $line_split[7];
				$request_mean_time = $line_split[6];
			}
			case (/^users/) { }
		}
	}
}

print "# Statistics Gathered:\n";
print "# Tsung Time: $ARGV[1]\n";
print "r200: $r200\n";
print "r400: $r400\n";
print "r500: $r500\n";
print "connected_max: $connected_max\n";
print "connect_count: $connect_count\n";
print "connect_mean_time: $connect_mean_time\n";
print "request_count: $request_count\n";
print "request_mean_time: $request_mean_time\n";
print "match: $match\n";
print "nomatch: $nomatch\n";

=pod
200.txt
connect.txt
connected.txt
finish_users_count.txt
match.txt
request.txt
users.txt
users_count.txt

requests
mean request time
mean connect time
max connected
max users
200s
404s
errors
match
nomatch
=cut
