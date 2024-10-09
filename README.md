# apachenifitest
Downloading the files from legacy system and upload it to alfresco

find /path/to/directory -type f -name "*.csv" -exec grep -l -P '^[^"]*[^"],[^"].*[^"]$' {} +

find /path/to/directory -type f -name "*.csv" -exec grep -n -H -P '(?<!"),(?!")' {} +
\


awk -F',' '{for(i=1; i<=NF; i++) $i="\"" $i "\""}1' OFS=',' input.csv > output.csv


