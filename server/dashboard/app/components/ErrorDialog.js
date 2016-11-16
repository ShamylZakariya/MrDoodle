let React = require('react');

let ErrorDialog = React.createClass({
	getDefaultProps:function(){
		return {
			error:null
		}
	},
	render: function(){

		return (
			<div className="error modal">
				<div className="window">
					<a className="close" onClick={this.handleClose}>Close</a>

					<div className="content">
						<h2>Error</h2>
						<p>{this.props.error}</p>
						<div className="buttonRow">
							<div className="button positive" onClick={this.handleClose}>OK</div>
						</div>
					</div>
				</div>
			</div>
		);
	},

	handleClose: function() {
		this.props.close();
	}
});

module.exports = ErrorDialog;