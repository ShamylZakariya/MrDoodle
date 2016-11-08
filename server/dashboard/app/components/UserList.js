var React = require('react');
var UserListItem = require('./UserListItem');

var UserList = React.createClass({

	getDefaultProps: function () {
		return {
			users: []
		}
	},

	render: function () {
		var userItems = this.props.users.map(function(user,index){
			return (
				<UserListItem user={user} click={this.props.click} key={user.accountId}/>
			)
		}.bind(this));

		return (
			<div className="users">
				<div className="toolbar">
					<h2>Users</h2>
					<div className="item reload" onClick={this.props.reload}>Reload</div>
				</div>
				<ul className="userList">
					{userItems}
				</ul>
			</div>
		)
	}
});

module.exports = UserList;