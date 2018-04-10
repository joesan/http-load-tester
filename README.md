# http-load-tester
Divide and Conquer Load Testing for WebSocket Endpoints

This is some concept that I implemented when I was doing lots of Akka and Scala. The idea is to load test HTTP servers that expose WebSocket
endpoints.

Here we have a configurable test setup to which I specify and simulate users opening WebSocket connections and bursting messages
to the server. You should see the server monitor once you run this load tester, the server will run like crazy! You can make or
break your server side aplications using this test tool! 

Using this concept, I was successfully able to demonstrate the drawbacks of a thread-per-request model of Java EE servers and highlight
the benefits of using a evented loop server model as it is done by most of the Scala based HTTP servers (Play Framework)!

This project is in a non-runnable state as I want to have it like this for now! I will probably show some love for this project and make it better and make
it generic such that it works for any data type. I will use the shapeless magic for this!

For now, if you are planning to write a DIY load test, just take the concept (divide-and-conquer-pattern from Akka) and implement it!
