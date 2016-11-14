let React = require('react');
let UserListItem = require('./UserListItem');

let UserList = React.createClass({

	getDefaultProps: function () {
		return {
			users: []
		}
	},

	render: function () {
		let userItems = this.props.users.map(function(user,index){
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