var $ = require('../jquery-3.1.1');
var React = require('react');
var moment = require('moment');

var UserDetail = React.createClass({

	getDefaultProps: function() {
		return {
			user: null
		}
	},

	getInitialState: function() {
		return {
			connectedDeviceCount: 0
		}
	},

	componentDidMount: function(){
		// perform a $.getJSON call to get the details on our user
		this.loadUserInfo();
		this.updateUserInfoInterval = setInterval(this.loadUserInfo,2000);
	},

	componentWillUnmount: function() {
		if (this.updateUserInfoInterval) {
			clearInterval(this.updateUserInfoInterval);
		}
	},

	render: function(){
		var user = this.props.user;
		var lastAccessDate = (new Date(user.lastAccessTimestamp * 1000));
		var formattedLastAccessDate = moment(lastAccessDate).format('MMMM Do YYYY, h:mm:ss a');

		var styles = {
			avatarImageStyle: {
				backgroundImage: (user.avatarUrl && user.avatarUrl.length) ? "url(" + user.avatarUrl + ")" : undefined
			}
		};

		var connectedMarkerClassName = "connectedMarker " + (this.state.connectedDeviceCount > 0 ? "connected" : "disconnected");

		return (
			<div className="userDetail">
				<div className="window">
					<a className="close" onClick={this.handleClose}>Close</a>

					<div className="userInfo">

						<div className="avatar">
							<div className="avatarImage" style={styles.avatarImageStyle}></div>
							<div className={connectedMarkerClassName}><span className="count">{this.state.connectedDeviceCount}</span></div>
						</div>
						<div className="email">{user.email}</div>
						<div className="id">{user.id}</div>
						<div className="lastAccessDate">{formattedLastAccessDate}</div>

					</div>
				</div>
			</div>
		)
	},

	loadUserInfo: function() {
		$.getJSON("http://localhost:4567/api/v1/dashboard/users/" + this.props.user.id)
			.done(data => {
				this.setState({
					connectedDeviceCount: data.connectedDevices
				});
			})
			.fail(e => {
				this.setState({
					connectedDeviceCount: 0
				});
			});
	},

	handleClose: function() {
		this.props.close();
	}

});

module.exports = UserDetail;