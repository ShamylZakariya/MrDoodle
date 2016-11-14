let React = require('react');
let ReactDOM = require('react-dom');

let ErrorView = require('./components/ErrorView');
let UserList = require('./components/UserList');
let UserDetail = require('./components/UserDetail');

let App = React.createClass({

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

		let userList = this.state.error ? null : <UserList users={this.state.users} click={this.showUserDetail} reload={this.performLoad}/>;
		let errorView = this.state.error ? <ErrorView error={this.state.error}/> : null;
		let userDetail = this.state.selectedUser ? <UserDetail user={this.state.selectedUser} close={this.handleCloseUserDetail}/> : null;

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
		console.log('App::performLoad');

		fetch("http://localhost:4567/api/v1/dashboard/users", { credentials: 'include' })
			.then(function(response){
				return response.json()
			})
			.then(data => {
				this.setState({
					users: data.users,
					error: null
				});
			})
			.catch(e => {
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