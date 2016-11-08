var React = require('react');
var ReactDOM = require('react-dom');
var $ = require("./jquery-3.1.1");

var ErrorView = require('./components/ErrorView');
var UserList = require('./components/UserList');
var UserDetail = require('./components/UserDetail');

var App = React.createClass({

	getInitialState: function() {
		return {
			users: [],
			error: null,
			selectedUser: null
		}
	},

	componentDidMount: function() {
		this.performLoad();
	},

	render: function() {

		var userList = this.state.error ? null : <UserList users={this.state.users} click={this.showUserDetail} reload={this.performLoad}/>;
		var errorView = this.state.error ? <ErrorView error={this.state.error}/> : null;
		var userDetail = this.state.selectedUser ? <UserDetail user={this.state.selectedUser} close={this.handleCloseUserDetail}/> : null;

		return (
			<div className="container">
				{userList}
				{errorView}
				{userDetail}
			</div>
		)
	},

	///////////////////////////////////////////////////////////////////

	performLoad: function() {
		console.log('App::performLoad')

		// for now, we're loading ALL users, ignoring page parameter
		$.getJSON("http://localhost:4567/api/v1/dashboard/users")
			.done(data => {
				this.setState({
					users: data.users,
					error: null
				});
			})
			.fail(e => {
				this.setState({
					users:[],
					error: e.statusText
				});
			});
	},

	handleCloseUserDetail: function(){
		this.setState({
			selectedUser: null
		})
	},

	showUserDetail: function(user) {
		this.setState({
			selectedUser: user
		});
	}

});

ReactDOM.render(
	<App />,
	document.getElementById('app')
);