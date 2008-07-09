require 'find'

if ARGV.length != 2
  puts "Usage: svn-sync.rb dist-dir svn-dir"
  exit 1
end

build = ARGV[0]
svn = ARGV[1]
to_add = `svn status #{svn}`.split("\n").grep(/^\?/).map{|path| path.sub(/^\?\s+/, '')}
Find.find(svn) do |f|
  Find.prune if f =~ /.svn/
  if not File.exist? f.sub(Regexp.new("^#{svn}"), build)
    puts `svn del #{f}`
  elsif to_add.member? f
    puts `svn add #{f}`
  end
end


