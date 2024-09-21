This is an interview take home assignment.
Original Repo:
https://github.com/paidy/interview

Problem redefine:
1. Implement the logic in forex which is a proxy service.
2. Forex would provide one API to get currency rate which is within 5 mins freshness.
3. Forex's request has auth check which limit 10000 request per day per token.

Notes: The original requirement was looking for live version. I just implement the OneFrameService, the naming can be changed. 

So, break down the problem even more to actual tasks and component:
1. OneFrameService Client to trigger the request and store the response.
2. Authentication component. Rate limiting and authticate the request.
3. Cache component. Fetch and store rates from OneFrameService in batch fashion to meet the request limitation.

How to use/test the service?
1. Install needed tools: docker, java
2. docker pull paidyinc/one-frame and docker run -p 8080:8080 paidyinc/one-frame
3. pull the frex code to local and in package directory run sbt run (this would need step 2 running first)
4. Send request to this url: http://localhost:8000/rates plus the currency rate you want to get. Example request: curl -H "token: key1" 'localhost:8000/rates?from=JPY&to=CAD'
Example result: 
{
  "from": "JPY",
  "to": "CAD",
  "price": 0.6383546951724097,
  "timestamp": "2024-09-20T21:34:19.832843-07:00"
}

Improvements TODO:
1. Authentication currently use fixed keys. Can use more mature things like bearer token or even some cloud service.
2. Cache is using local in memory cache. Can also use cloud version in case the service use case gets more complicated.
3. Unit test missing.

