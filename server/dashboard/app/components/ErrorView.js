let React = require('react');

let ErrorView = React.createClass({
	getDefaultProps:function(){
		return {
			error:null
		}
	},
	render: function(){
		if (this.props.error && this.props.error.length) {
			return <div className="errorView">{this.props.error}</div>
		} else {
			return <div className="errorView">No error</div>
		}
	}
});

module.exports = ErrorView;