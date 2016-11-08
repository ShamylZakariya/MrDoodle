var React = require('react');
var moment = require('moment');

var UserListItem = React.createClass({
	getDefaultProps: function() {
		return {
			user: {
				avatarUrl: null,
				id: null,
				email: null,
				lastAccessTimestamp: 0
			}
		}
	},

	render: function () {
		var user = this.props.user;
		var lastAccessDate = (new Date(user.lastAccessTimestamp * 1000));
		var formattedLastAccessDate = moment(lastAccessDate).format('MMMM Do YYYY, h:mm:ss a');

		return (
			<li className="userListItem" onClick={this.handleClick}>
				<div className="avatar"><img src={user.avatarUrl}/></div>
				<div className="info">
					<div className="email">{user.email}</div>
					<div className="id">{user.id}</div>
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