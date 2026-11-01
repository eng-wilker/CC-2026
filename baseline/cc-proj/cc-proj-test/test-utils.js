'use strict';

/***
 * Exported functions to be used in the testing scripts.
 */
module.exports = {
  uploadImageBody,
  genNewUser,
  genNewUserReply,
  selectUser,
  selectUserSkewed,
  genNewAuction,
  genNewBid,
  genNewQuestion,
  genNewQuestionReply,
  decideToCoverBid,
  decideToReply,
  decideNextAction,
  random80,
}


// The old 'faker' package was unpublished in 2022. @faker-js/faker replaces it, and
// renamed the name.* namespace to person.*; everything else used here is unchanged.
const { fakerEN_US: Faker } = require('@faker-js/faker')
const fs = require('fs')
const path = require('path')

var images = []
var users = []

// Auxiliary function to select an element from an array
Array.prototype.sample = function(){
	   return this[Math.floor(Math.random()*this.length)]
}

// Auxiliary function to select an element from an array
Array.prototype.sampleSkewed = function(){
	return this[randomSkewed(this.length)]
}

// Returns a random value, from 0 to val
function random( val){
	return Math.floor(Math.random() * val)
}

// Returns the user with the given id
function findUser( id){
	for( var u of users) {
		if( u.id === id)
			return u;
	}
	return null
}

// Returns a random value, from 0 to val
function randomSkewed( val){
	let beta = Math.pow(Math.sin(Math.random()*Math.PI/2),2)
	let beta_left = (beta < 0.5) ? 2*beta : 2*(1-beta);
	return Math.floor(beta_left * val)
}


// Loads data about images from disk
function loadData() {
	var basedir
	if( fs.existsSync( '/images')) 
		basedir = '/images'
	else
		basedir =  'images'	
	fs.readdirSync(basedir).forEach( file => {
		if( path.extname(file) === ".jpeg") {
			var img  = fs.readFileSync(basedir + "/" + file)
			images.push( img)
		}
	})
	var str;
	if( fs.existsSync('users.data')) {
		str = fs.readFileSync('users.data','utf8')
		users = JSON.parse(str)
	} 
}

loadData();

/**
 * Sets the body to an image, when using images.
 */
function uploadImageBody(requestParams, context, ee, next) {
	requestParams.body = images.sample()
	return next()
}

/**
 * Generate data for a new user using Faker
 */
function genNewUser(context, events, done) {
	const first = `${Faker.person.firstName()}`
	const last = `${Faker.person.lastName()}`
	context.vars.id = first + "." + last
	context.vars.name = first + " " + last
	context.vars.pwd = `${Faker.internet.password()}`
	return done()
}


/**
 * Process reply for of new users to store the id on file
 */
function genNewUserReply(requestParams, response, context, ee, next) {
	if( response.statusCode >= 200 && response.statusCode < 300 && response.body.length > 0)  {
		let u = JSON.parse( response.body)
		// The server never echoes the password back, so keep the generated one - otherwise
		// users.data ends up with empty passwords and every later login fails with 401.
		u.pwd = context.vars.pwd
		users.push(u)
		fs.writeFileSync('users.data', JSON.stringify(users));
	}
    return next()
}

/**
 * Select user
 */
function selectUser(context, events, done) {
	if( users.length > 0) {
		let user = users.sample()
		context.vars.user = user.id
		context.vars.pwd = user.pwd
	} else {
		delete context.vars.user
		delete context.vars.pwd
	}
	return done()
}


/**
 * Select user
 */
function selectUserSkewed(context, events, done) {
	if( users.length > 0) {
		let user = users.sampleSkewed()
		context.vars.user = user.id
		context.vars.pwd = user.pwd
	} else {
		delete context.vars.user
		delete context.vars.pwd
	}
	return done()
}

/**
 * Generate data for a new channel
 * Besides the variables for the auction, initializes the following vars:
 * numBids - number of bids to create, if batch creating 
 * numQuestions - number of questions to create, if batch creating 
 * bidValue - price for the next bid
 */
function genNewAuction(context, events, done) {
	context.vars.title = `${Faker.commerce.productName()}`
	context.vars.description = `${Faker.commerce.productDescription()}`
	// Faker.commerce.price() returns a string, so keep it a number: with a string, the '+'
	// below concatenates instead of adding and every bid just appends digits to the previous
	// one. From the third bid on those digits fall below the precision of the server's 32-bit
	// float field, the bid no longer beats the current winner, and the auction rejects it (403).
	context.vars.minimumPrice = Number(Faker.commerce.price())
	context.vars.bidValue = context.vars.minimumPrice + random(3)
	var maxBids = 5
	if( typeof context.vars.maxBids !== 'undefined')
		maxBids = context.vars.maxBids;
	var maxQuestions = 2
	if( typeof context.vars.maxQuestions !== 'undefined')
		maxQuestions = context.vars.maxQuestions;
	var d = new Date();
	// One to three hours. The backend rejects bids on auctions whose endTime has passed,
	// so the window has to outlast a whole lab session - five minutes meant that by the
	// time the workload ran, every auction from create-auctions.yml had already closed.
	d.setTime(Date.now() + 3600000 + random( 7200000));
	context.vars.endTime = d.toISOString();
	// An auction is always created open, so there is no status to send. One in five gets no
	// bids and no questions, so that not every auction ends up with activity.
	if( Math.random() > 0.2) { 
		context.vars.numBids = random( maxBids);
		context.vars.numQuestions = random( maxQuestions);
	} else {
		delete context.vars.numBids;
		delete context.vars.numQuestions;
	}
	return done()
}

/**
 * Generate data for a new bid
 */
function genNewBid(context, events, done) {
	if( typeof context.vars.bidValue == 'undefined') {
		if( typeof context.vars.minimumPrice == 'undefined') {
			context.vars.bidValue = random(100)
		} else {
			context.vars.bidValue = context.vars.minimumPrice + random(3)
		}
	}
	context.vars.value = context.vars.bidValue;
	context.vars.bidValue = context.vars.bidValue + 1 + random(3)
	return done()
}

/**
 * Generate data for a new question
 */
function genNewQuestion(context, events, done) {
	context.vars.text = `${Faker.lorem.paragraph()}`;
	return done()
}

/**
 * Generate data for a new reply
 */
function genNewQuestionReply(context, events, done) {
	delete context.vars.reply;
	if( Math.random() > 0.5) {
		if( typeof context.vars.auctionUser !== 'undefined') {
			var user = findUser( context.vars.auctionUser);
			if( user != null) {
				context.vars.auctionUserPwd = user.pwd;
				context.vars.reply = `${Faker.lorem.paragraph()}`;
			}
		}
	} 
	return done()
}


// All virtual users run in this one process, so these are shared by all of them: without them,
// two users acting at the same time would bid the same value or reply to the same question,
// and the server rejects the second one (403).
var highestBid = {}          // auction id -> highest value bid by this run
var repliedQuestions = new Set()

/**
 * Decide whether to bid on auction or not
 * assuming: user context.vars.user; auction context.vars.auction (from GET /auction/{id})
 * The server rejects (403) a bid on an auction that has ended, a bid below the minimum price,
 * and a bid that does not beat the current winner - so check all three here.
 */
function decideToCoverBid(context, events, done) {
	delete context.vars.value;
	let auction = context.vars.auction
	delete context.vars.auction          // so a failed read does not leave the previous one here
	if( typeof context.vars.user === 'undefined' || auction == null || typeof auction !== 'object')
		return done()
	// nothing closes an auction when its end time passes, so lists still return ended ones
	if( auction.status !== 'open' || new Date(auction.endTime).getTime() < Date.now() + 5000)
		return done()
	let winner = auction.winnerBid
	if( winner != null && winner.user === context.vars.user)
		return done()                     // already winning
	if( Math.random() > 0.5) {
		let current = Math.max( winner != null ? winner.value : 0, highestBid[auction.id] || 0)
		let value = current > 0 ? current + 1 + random(3) : auction.minimumPrice + random(3)
		value = Math.max( value, auction.minimumPrice)
		highestBid[auction.id] = value
		context.vars.value = value;
		context.vars.auctionId = auction.id;
	}
	return done()
}

/**
 * Decide whether to reply, and to which question
 * assuming: user context.vars.user; auction context.vars.$loopElement; questions context.vars.questionsLst
 * Only the owner of the auction can reply, and only once, so pick a question with no reply yet.
 */
function decideToReply(context, events, done) {
	delete context.vars.reply;
	delete context.vars.questionId;
	let auction = context.vars.$loopElement
	let questions = context.vars.questionsLst
	if( typeof context.vars.user !== 'undefined' && typeof auction !== 'undefined' &&
			auction.owner === context.vars.user &&
			Array.isArray(questions)) {
		let open = questions.filter( q => (q.reply === null || typeof q.reply === 'undefined') &&
				!repliedQuestions.has(q.id))
		if( open.length > 0) {
			context.vars.questionId = open.sample().id
			repliedQuestions.add(context.vars.questionId)
			context.vars.reply = `${Faker.lorem.paragraph()}`;
		}
	}
	return done()
}


/**
 * Decide next action
 * 0 -> browse popular
 * 1 -> browse recent
 */
function decideNextAction(context, events, done) {
	delete context.vars.auctionId;
	let rnd = Math.random()
	if( rnd < 0.075)
		context.vars.nextAction = 0; // browsing recent
	else if( rnd < 0.15)
		context.vars.nextAction = 1; // browsing popular
	else if( rnd < 0.225)
		context.vars.nextAction = 2; // browsing user
	else if( rnd < 0.3)
		context.vars.nextAction = 3; // create an auction
	else if( rnd < 0.8)
		context.vars.nextAction = 4; // checking auction
	else if( rnd < 0.95)
		context.vars.nextAction = 5; // do a bid
	else
		context.vars.nextAction = 6; // post a message
	if( context.vars.nextAction == 2) {
		if( Math.random() < 0.5)
			context.vars.user2 = context.vars.user
		else {
			let user = users.sample()
			context.vars.user2 = user.id
		}
	}
	if( context.vars.nextAction == 3) {
		context.vars.title = `${Faker.commerce.productName()}`
		context.vars.description = `${Faker.commerce.productDescription()}`
		// a number, not a string - see genNewAuction
		context.vars.minimumPrice = Number(Faker.commerce.price())
		context.vars.bidValue = context.vars.minimumPrice + random(3)
		var d = new Date();
		d.setTime(Date.now() + 3600000 + random( 7200000));
		context.vars.endTime = d.toISOString();
	}
	if( context.vars.nextAction >= 4) {
		let r = random(3)
		var auct = null
		if( r == 2 && typeof context.vars.auctionsLst == 'undefined')
			r = 1;
		if( r == 2)
  			auct = context.vars.auctionsLst.sample();
		else if( r == 1)
  			auct = context.vars.recentLst.sample();
		else if( r == 0)
  			auct = context.vars.popularLst.sample();
		if( auct == null) {
			return decideNextAction(context,events,done);
		}
		context.vars.auctionId = auct.id
		context.vars.imageId = auct.imageId
	}
	if( context.vars.nextAction == 6)   // 6 is "post a message" - 5 is the bid, which has no text
		context.vars.text = `${Faker.lorem.paragraph()}`;

	return done()
}


/**
 * Return true with probability 80%
 */
function random80(context, next) {
  const continueLooping = Math.random() < 0.8
  return next(continueLooping);
}
