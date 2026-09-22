#!/usr/bin/env perl
# 修复「字符串字面量被误伤」：把 SQL 单引号内的新表名还原成旧表名。
# 背景：全词替换 \b<table>\b 会命中 'model.models'、'/api/users'、'https://openrouter.ai/models'
#       这类字面量（. / - 都是词边界），导致菜单编码、API 路径、外部 URL 被改坏。
# 用法：perl fix-string-literals.pl <mapping.csv> <target.sql> [...]
use strict;
use warnings;

my ($mapfile, @files) = @ARGV;
die "usage: $0 mapping.csv file.sql [...]\n" unless $mapfile && @files;

open my $mh, '<', $mapfile or die "cannot read $mapfile: $!";
my %rev;
while (my $line = <$mh>) {
    chomp $line;
    $line =~ s/\r//g;
    next if $line =~ /^\s*#/;
    my ($old, $new) = split /,/, $line;
    next unless defined $old && defined $new && $old ne '' && $new ne '';
    $old =~ s/^\x{ef}\x{bb}\x{bf}//;
    $rev{$new} = $old;
}
close $mh;
# 长名优先，避免短名先命中
my @keys = sort { length($b) <=> length($a) } keys %rev;

for my $file (@files) {
    local $/;
    open my $fh, '<', $file or die "cannot read $file: $!";
    my $c = <$fh>;
    close $fh;
    my $before = $c;
    # 只处理单引号字面量内部
    $c =~ s{('(?:[^']*)')}{
        my $s = $1;
        for my $k (@keys) { $s =~ s/\b\Q$k\E\b/$rev{$k}/g; }
        $s;
    }ge;
    if ($c ne $before) {
        open my $out, '>', $file or die "cannot write $file: $!";
        print $out $c;
        close $out;
        print "fixed: $file\n";
    } else {
        print "unchanged: $file\n";
    }
}
