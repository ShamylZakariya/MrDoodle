var React = require('react');
var moment = require('moment');

var UserListItem = React.createClass({
	getDefaultProps: function() {
		return {
			user: {
				accountId: null,
				avatarUrl: null,
				email: null,
				lastAccessTimestampSeconds: 0
			}
		}
	},

	render: function () {
		var user = this.props.user;
		var lastAccessDate = (new Date(user.lastAccessTimestampSeconds * 1000));
		var formattedLastAccessDate = moment(lastAccessDate).format('MMMM Do YYYY, h:mm:ss a');

		var avatarStyle = {
			backgroundImage: (user.avatarUrl && user.avatarUrl.length) ? "url(" + user.avatarUrl + ")" : undefined
		};

		return (
			<li className="userListItem" onClick={this.handleClick}>
				<div className="avatar" style={avatarStyle}></div>
				<div className="info">
					<div className="email">{user.email}</div>
					<div className="id">{user.accountId}</div>
					<div className="lastAccessDate">{formattedLastAccessDate}</div>
				</div>
			</li>
		)
	},

	handleClick: function() {
		this.props.click(this.props.user);
	}

});

module.exports = UserListItem;