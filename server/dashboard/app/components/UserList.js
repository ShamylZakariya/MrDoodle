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
				<UserListItem user={user} click={this.props.click} key={user.id}/>
			)
		}.bind(this));

		return (
			<div className="users">
				<h2>Users</h2>
				<ul className="userList">
					{userItems}
				</ul>
			</div>
		)
	}
});

module.exports = UserList;