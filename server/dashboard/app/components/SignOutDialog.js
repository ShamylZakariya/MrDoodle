let React = require('react');

let SignOutDialog = React.createClass({

	getDefaultProps: function() {
		return {
			close: function(){},
			signOut: function(){}
		}
	},

	render: function() {

		return (
			<div className="signOutDialog modal">
				<div className="window">
					<a className="close" onClick={this.handleCancel}>Close</a>

					<div className="content">
						<h2>Sign out?</h2>

						<div className="buttonRow">
							<div className="button negative" onClick={this.handleCancel}>Cancel</div>
							<div className="button destructive" onClick={this.handleSignOut}>Sign Out</div>
						</div>
					</div>
				</div>
			</div>
		)
	},

	handleCancel: function() {
		this.props.close();
	},

	handleSignOut: function() {
		this.props.close();
		this.props.signOut();
	}

});

module.exports = SignOutDialog;