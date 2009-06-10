import sys, re, optparse

def get_starting(line):
	return re.findall(r'starting renderTAL on (\w+)', line)

def get_finished(line):
	return re.findall(r'finished rendering (\w+) in (\d+\.\d+)', line)

def main():
	# parse command-line options
	usage = "usage: %prog [options] logfile"
	parser = optparse.OptionParser(usage)
	parser.add_option("-l", "--limit", dest="limit", metavar="N",
					  help="Only parse last N calls to renderTAL")
	(options, args) = parser.parse_args()
	if len(args) == 0:
		parser.error("No logfile given.")

	lines = open(args[0]).readlines()
	if options.limit:
		# start scanning backwards to grab only the last N requests
		count = 0
		stack = 0
		i = len(lines) - 1
		while(i > 0 and count < int(options.limit)):
			line = lines[i]
			if get_finished(line):
				stack += 1
			elif get_starting(line):
				stack -= 1
				if stack == 0:
					count += 1
			i -= 1

	# begin parsing selected lines
	stack = 0
	last_query = None
	num_queries = 0
	query_time = 0.0
	for line in lines[i+1:]:
		starting = get_starting(line)
		finished = get_finished(line)
		query = re.findall(r'running (?:hits )?query (.*)$', line)
		time = re.findall(r'took (\d+\.\d+) seconds', line)
		if starting:
			print "%s%s" % ("  | "*stack, starting[0])
			stack += 1
		if finished:
			stack -= 1
			print "%s  |- %s seconds total (%s)" % ("  | "*stack, finished[0][1], finished[0][0])
			if stack == 0:
				print "-"*60
		if time and stack > 0:
			print "%s %s seconds for query %s" % ("  | "*stack, time[0], last_query)
			num_queries += 1
			query_time += float(time[0])
		if query:
			last_query = query[0]

	times = re.findall(r'took (\d+\.\d+) seconds',' '.join(lines))
	print "%i db queries in analyzed lines took %f seconds total" % (num_queries, query_time)

if __name__ == '__main__':
	main()
