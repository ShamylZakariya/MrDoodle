
console.log("Making test call to dashboard API");

$.getJSON("http://localhost:4567/api/v1/dashboard/users")
	.done( e => {
		console.log('got data: ', e);
	})
	.fail( e => {
		console.error("failed: ", e);
	});
