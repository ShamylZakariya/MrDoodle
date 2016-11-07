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
				<ul className="user">
					<li className="avatar"><img src={user.avatarUrl}/></li>
					<li className="email">{user.email}</li>
					<li className="id"><span className="value">{user.id}</span></li>
					<li className="lastAccessDate">{formattedLastAccessDate}</li>
				</ul>
			</li>
		)
	},

	handleClick: function() {
		this.props.click(this.props.user);
	}

});

module.exports = UserListItem;