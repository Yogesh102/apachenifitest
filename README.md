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



          <Subject><NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified">yprjapati@delta.org</NameID><SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer"><SubjectConfirmationData InResponseTo="ID_8fb3b9b2-4821-4ffb-a011-65b7233ba16f" NotOnOrAfter="2025-07-25T04:13:32.041Z" Recipient="https://mot-alfrescofsint-vip/auth/realms/alfresco/broker/saml/endpoint"/></SubjectConfirmation></Subject><Conditions NotBefore="2025-07-25T03:08:32.041Z" NotOnOrAfter="2025-07-25T04:13:32.041Z"><AudienceRestriction><Audience>https://mot-alfrescofsint-vip/auth/realms/alfresco</Audience></AudienceRestriction></Conditions><AttributeStatement><Attribute Name="http://schemas.microsoft.com/identity/claims/tenantid"><AttributeValue>fa8362b9-ef27-44e1-bf1c-b6e97717881d</AttributeValue></Attribute><Attribute Name="http://schemas.microsoft.com/identity/claims/objectidentifier"><AttributeValue>d3a8a733-96de-4d48-a705-744e7c13e574</AttributeValue></Attribute><Attribute Name="http://schemas.microsoft.com/identity/claims/displayname"><AttributeValue>Yogesh Prjapati</AttributeValue></Attribute><Attribute Name="http://schemas.microsoft.com/identity/claims/identityprovider"><AttributeValue>https://sts.windows.net/fa8362b9-ef27-44e1-bf1c-b6e97717881d/</AttributeValue></Attribute><Attribute Name="http://schemas.microsoft.com/claims/authnmethodsreferences"><AttributeValue>http://schemas.microsoft.com/ws/2008/06/identity/authenticationmethod/windows</AttributeValue></Attribute><Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname"><AttributeValue>Yogesh</AttributeValue></Attribute><Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname"><AttributeValue>Prjapati</AttributeValue></Attribute><Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"><AttributeValue>yprjapati@delta.org</AttributeValue></Attribute><Attribute Name="http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name"><AttributeValue>yprjapati@delta.org</AttributeValue></Attribute></AttributeStatement><AuthnStatement AuthnInstant="2025-07-25T03:13:01.066Z" SessionIndex="_cf6e1bde-b122-4aba-b0f1-86b5e7954600"><AuthnContext><AuthnContextClassRef>urn:federation:authentication:windows</AuthnContextClassRef></AuthnContext></AuthnStatement></Assertion></samlp:Response>


