/**
 * Debounce a function call. Returns a function which when called handles debouncing internally,
 * and forwards its arguments to the debounced function you provide
 * @param {Number} millis the debounce delay
 * @param {Function} f the function to debounce
 * @returns {Function}
 */
let debounce = function (millis, f) {

	let timeout = null;
	return function () {
		if (timeout) {
			clearTimeout(timeout);
		}

		let args = arguments;
		timeout = setTimeout(function () {
			f.apply(null, args);
		}, millis);
	}
};

module.exports = debounce;