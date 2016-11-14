var HtmlWebpackPlugin = require('html-webpack-plugin');
var HTMLWebpackPluginConfig = new HtmlWebpackPlugin({
	template: __dirname + '/app/index.html',
	filename: 'index.html',
	inject: 'body'
});

var CopyWebpackPlugin = require('copy-webpack-plugin');
var path = require('path');


module.exports = {
	entry: [
		'whatwg-fetch',
		'promise-polyfill',
		'./app/app.js'
	],
	output: {
		path: __dirname + '/dist',
		filename: "app.bundle.js"
	},
	module: {
		loaders: [
			{
				test: /\.js$/,
				exclude: /node_modules/,
				include: __dirname + '/app',
				loader: "babel-loader"
			}
		]
	},
	plugins: [
		HTMLWebpackPluginConfig,
		new CopyWebpackPlugin([
			{
				context: './app/css',
				from: "*.css",
				to: "./css"
			},
			{
				context: './app/images',
				from: "*",
				to: "./images"
			}
		])
	]
};