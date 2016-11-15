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
				<ul className="userList">
					{userItems}
				</ul>
			</div>
		)
	}
});

module.exports = UserList;