# apachenifitest
Downloading the files from legacy system and upload it to alfresco

find /path/to/directory -type f -name "*.csv" -exec grep -l -P '^[^"]*[^"],[^"].*[^"]$' {} +

find /path/to/directory -type f -name "*.csv" -exec grep -n -H -P '(?<!"),(?!")' {} +
\


awk -F',' '{for(i=1; i<=NF; i++) $i="\"" $i "\""}1' OFS=',' input.csv > output.csv


for file in *"(Change only).zip"; do
    mv "$file" "$(echo "$file" | sed 's/Change/change/')";
done


header=$(head -n 1 Finance1.csv)

tail -n +2 Finance1.csv | split -l $((($(wc -l < Finance1.csv)-1)/10)) - Finance_part_
for file in Finance_part_*; do
    sed -i "1i$header" "$file"
done

<Connector port="9443" protocol="org.apache.coyote.http11.Http11NioProtocol"
           SSLEnabled="true" maxThreads="150" scheme="https" secure="true"
           keystoreFile="${JAVA_HOME}/lib/security/cacerts"
           keystoreType="JKS" keystorePass="changeit"
           keyAlias="your-cert-alias"
           clientAuth="false" sslProtocol="TLS" />


