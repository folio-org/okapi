echo The following login should fail
curl -D -  -H X-Okapi-Token:t1:peter:10058b75cb0b719d5a9efb39e97416bc -w'\n'  -X POST  -d @login-bad.json http://localhost:9020/authn/login
echo

echo The following login should succeed
curl -D -  -H X-Okapi-Token:t1:peter:10058b75cb0b719d5a9efb39e97416bc -w'\n'  -X POST  -d @login1.json http://localhost:9020/authn/login
echo
