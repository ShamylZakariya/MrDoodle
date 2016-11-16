let React = require('react');

let UserStatusBar = React.createClass({

	getDefaultProps: function() {
		return {
			totalUsers: 0,
			totalConnectedUsers: 0,
			totalConnectedDevices: 0
		}
	},

	render: function() {
		return (
			<div className="statusbar">
				<div className="content">
					<div className="item">Total Users: <span className="value">{this.props.totalUsers}</span></div>
					<div className="item">Connected Users: <span className="value">{this.props.totalConnectedUsers}</span></div>
					<div className="item">Connected Devices: <span className="value">{this.props.totalConnectedDevices}</span></div>
				</div>
			</div>
		)
	}

});

module.exports = UserStatusBar;