/******************************************************************************
 * Copyright © 2020-2021    The BENED Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * BENED software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS, $, undefined) {

	NRS.lastTransactions = "";
	NRS.unconfirmedTransactions = [];
	NRS.unconfirmedTransactionIds = "";
	NRS.unconfirmedTransactionsChange = true;
  NRS.isHierarchyTableMinified = false;
  var colors = ["#dff4d5", "#ead5f4", "#f4efd5", "#f4d5df"];
  NRS.nextColor = function(sublevel) {
    return "color"+(sublevel%4 + 1);
    // return colors[sublevel%colors.length];
  }

	NRS.handleIncomingTransactions = function(transactions, confirmedTransactionIds) {
		var oldBlock = (confirmedTransactionIds === false); //we pass false instead of an [] in case there is no new block..

		if (typeof confirmedTransactionIds != "object") {
			confirmedTransactionIds = [];
		}

		if (confirmedTransactionIds.length) {
			NRS.lastTransactions = confirmedTransactionIds.toString();
		}

		if (confirmedTransactionIds.length || NRS.unconfirmedTransactionsChange) {
			transactions.sort(NRS.sortArray);
		}
		//Bug with popovers staying permanent when being open
		$('div.popover').hide();
		$('.td_transaction_phasing div.show_popover').popover('hide');

		//always refresh peers and unconfirmed transactions..
		if (NRS.currentPage == "peers") {
			NRS.incoming.peers();
		} else if (NRS.currentPage == "transactions"
            && $('#transactions_type_navi').find('li.active a').attr('data-transaction-type') == "unconfirmed") {
			NRS.incoming.transactions();
		} else {
			if (NRS.currentPage != 'messages' && (!oldBlock || NRS.unconfirmedTransactionsChange)) {
				if (NRS.incoming[NRS.currentPage]) {
					NRS.incoming[NRS.currentPage](transactions);
				}
			}
		}
		if (!oldBlock || NRS.unconfirmedTransactionsChange) {
			// always call incoming for messages to enable message notifications
			NRS.incoming['messages'](transactions);
			NRS.updateNotifications();
			NRS.setPhasingNotifications();
		}
	};

	NRS.getUnconfirmedTransactions = function(callback) {
		NRS.sendRequest("getUnconfirmedTransactions", {
			"account": NRS.account
		}, function(response) {
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var unconfirmedTransactions = [];
				var unconfirmedTransactionIds = [];

				response.unconfirmedTransactions.sort(function(x, y) {
					if (x.timestamp < y.timestamp) {
						return 1;
					} else if (x.timestamp > y.timestamp) {
						return -1;
					} else {
						return 0;
					}
				});

				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
					var unconfirmedTransaction = response.unconfirmedTransactions[i];
					unconfirmedTransaction.confirmed = false;
					unconfirmedTransaction.unconfirmed = true;
					unconfirmedTransaction.confirmations = "/";

					if (unconfirmedTransaction.attachment) {
						for (var key in unconfirmedTransaction.attachment) {
							if (!unconfirmedTransaction.attachment.hasOwnProperty(key)) {
								continue;
							}
							if (!unconfirmedTransaction.hasOwnProperty(key)) {
								unconfirmedTransaction[key] = unconfirmedTransaction.attachment[key];
							}
						}
					}
					unconfirmedTransactions.push(unconfirmedTransaction);
					unconfirmedTransactionIds.push(unconfirmedTransaction.transaction);
				}
				NRS.unconfirmedTransactions = unconfirmedTransactions;
				var unconfirmedTransactionIdString = unconfirmedTransactionIds.toString();
				if (unconfirmedTransactionIdString != NRS.unconfirmedTransactionIds) {
					NRS.unconfirmedTransactionsChange = true;
					NRS.setUnconfirmedNotifications();
					NRS.unconfirmedTransactionIds = unconfirmedTransactionIdString;
				} else {
					NRS.unconfirmedTransactionsChange = false;
				}

				if (callback) {
					callback(unconfirmedTransactions);
				}
			} else {
				NRS.unconfirmedTransactions = [];
				if (NRS.unconfirmedTransactionIds) {
					NRS.unconfirmedTransactionsChange = true;
					NRS.setUnconfirmedNotifications();
				} else {
					NRS.unconfirmedTransactionsChange = false;
				}

				NRS.unconfirmedTransactionIds = "";
				if (callback) {
					callback([]);
				}
			}
		});
	};

	NRS.getInitialTransactions = function() {
		NRS.sendRequest("getBlockchainTransactions", {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 9
		}, function(response) {
			if (response.transactions && response.transactions.length) {
				var transactions = [];
				var transactionIds = [];

				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					transactions.push(transaction);
					transactionIds.push(transaction.transaction);
				}
				NRS.getUnconfirmedTransactions(function() {
					NRS.loadPage('dashboard');
				});
			} else {
				NRS.getUnconfirmedTransactions(function() {
					NRS.loadPage('dashboard');
				});
			}
		});
	};

	NRS.getNewTransactions = function() {
		//check if there is a new transaction..
		if (!NRS.blocks[0]) {
			return;
		}
        NRS.sendRequest("getBlockchainTransactions", {
			"account": NRS.account,
			"timestamp": NRS.blocks[0].timestamp + 1,
			"firstIndex": 0,
			"lastIndex": 0
		}, function(response) {
			//if there is, get latest 10 transactions
			if (response.transactions && response.transactions.length) {
				NRS.sendRequest("getBlockchainTransactions", {
					"account": NRS.account,
					"firstIndex": 0,
					"lastIndex": 9
				}, function(response) {
					if (response.transactions && response.transactions.length) {
						var transactionIds = [];

						$.each(response.transactions, function(key, transaction) {
							transactionIds.push(transaction.transaction);
							response.transactions[key].confirmed = true;
						});

						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(response.transactions.concat(unconfirmedTransactions), transactionIds);
						});
					} else {
						NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							NRS.handleIncomingTransactions(unconfirmedTransactions);
						});
					}
				});
			} else {
				NRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
					NRS.handleIncomingTransactions(unconfirmedTransactions);
				});
			}
		});
	};

	NRS.addUnconfirmedTransaction = function(transactionId, callback) {
		NRS.sendRequest("getTransaction", {
			"transaction": transactionId
		}, function(response) {
			if (!response.errorCode) {
				response.transaction = transactionId;
				response.confirmations = "/";
				response.confirmed = false;
				response.unconfirmed = true;

				if (response.attachment) {
					for (var key in response.attachment) {
                        if (!response.attachment.hasOwnProperty(key)) {
                            continue;
                        }
						if (!response.hasOwnProperty(key)) {
							response[key] = response.attachment[key];
						}
					}
				}
				var alreadyProcessed = false;
				try {
					var regex = new RegExp("(^|,)" + transactionId + "(,|$)");
					if (regex.exec(NRS.lastTransactions)) {
						alreadyProcessed = true;
					} else {
						$.each(NRS.unconfirmedTransactions, function(key, unconfirmedTransaction) {
							if (unconfirmedTransaction.transaction == transactionId) {
								alreadyProcessed = true;
								return false;
							}
						});
					}
				} catch (e) {
                    NRS.logConsole(e.message);
                }

				if (!alreadyProcessed) {
					NRS.unconfirmedTransactions.unshift(response);
				}
				if (callback) {
					callback(alreadyProcessed);
				}
				if (NRS.currentPage == 'transactions' || NRS.currentPage == 'dashboard') {
					$('div.popover').hide();
					$('.td_transaction_phasing div.show_popover').popover('hide');
					NRS.incoming[NRS.currentPage]();
				}

				NRS.getAccountInfo();
			} else if (callback) {
				callback(false);
			}
		});
	};

	NRS.sortArray = function(a, b) {
		return b.timestamp - a.timestamp;
	};

	NRS.getTransactionIconHTML = function(type, subtype, senderRS) {
		var iconHTML = NRS.transactionTypes[type]['iconHTML'] + " " + NRS.transactionTypes[type]['subTypes'][subtype]['iconHTML'];
		var tooltip = $.t(NRS.transactionTypes[type].subTypes[subtype].i18nKeyTitle);
    if (senderRS != null && String(senderRS) == "bened5h5hprs4uyta8gy83") {
      iconHTML = "<img src='img/icon-pickaxe.svg' style='width:12px;height:12px;'/>";
      tooltip = $.t("softMG");
    }
		return '<span title="' + tooltip + '" class="label label-primary" style="font-size:12px;">' + iconHTML + '</span>';
	};

	NRS.addPhasedTransactionHTML = function(t) {
		var $tr = $('.tr_transaction_' + t.transaction + ':visible');
		var $tdPhasing = $tr.find('.td_transaction_phasing');
		var $approveBtn = $tr.find('.td_transaction_actions .approve_transaction_btn');

		if (t.attachment && t.attachment["version.Phasing"] && t.attachment.phasingVotingModel != undefined) {
			NRS.sendRequest("getPhasingPoll", {
				"transaction": t.transaction,
				"countVotes": true
			}, function(responsePoll) {
				if (responsePoll.transaction) {
					NRS.sendRequest("getPhasingPollVote", {
						"transaction": t.transaction,
						"account": NRS.accountRS
					}, function(responseVote) {
						var attachment = t.attachment;
						var vm = attachment.phasingVotingModel;
						var minBalance = parseFloat(attachment.phasingMinBalance);
						var mbModel = attachment.phasingMinBalanceModel;

						if ($approveBtn) {
							var disabled = false;
							var unconfirmedTransactions = NRS.unconfirmedTransactions;
							if (unconfirmedTransactions) {
								for (var i = 0; i < unconfirmedTransactions.length; i++) {
									var ut = unconfirmedTransactions[i];
									if (ut.attachment && ut.attachment["version.PhasingVoteCasting"] && ut.attachment.transactionFullHashes && ut.attachment.transactionFullHashes.length > 0) {
										if (ut.attachment.transactionFullHashes[0] == t.fullHash) {
											disabled = true;
											$approveBtn.attr('disabled', true);
										}
									}
								}
							}
							if (!disabled) {
								if (responseVote.transaction) {
									$approveBtn.attr('disabled', true);
								} else {
									$approveBtn.attr('disabled', false);
								}
							}
						}

						if (!responsePoll.result) {
							responsePoll.result = 0;
						}

						var state = "";
						var color = "";
						var icon = "";
						var minBalanceFormatted = "";
                        var finished = attachment.phasingFinishHeight <= NRS.lastBlockHeight;
						var finishHeightFormatted = String(attachment.phasingFinishHeight);
						var percentageFormatted = attachment.phasingQuorum > 0 ? NRS.calculatePercentage(responsePoll.result, attachment.phasingQuorum, 0) + "%" : "";
						var percentageProgressBar = attachment.phasingQuorum > 0 ? Math.round(responsePoll.result * 100 / attachment.phasingQuorum) : 0;
						var progressBarWidth = Math.round(percentageProgressBar / 2);
                        var approvedFormatted;
						if (responsePoll.approved || attachment.phasingQuorum == 0) {
							approvedFormatted = "Yes";
						} else {
							approvedFormatted = "No";
						}

						if (finished) {
							if (responsePoll.approved) {
								state = "success";
								color = "#00a65a";
							} else {
								state = "danger";
								color = "#f56954";
							}
						} else {
							state = "warning";
							color = "#f39c12";
						}

						var $popoverTable = $("<table class='table table-striped'></table>");
						var $popoverTypeTR = $("<tr><td></td><td></td></tr>");
						var $popoverVotesTR = $("<tr><td>" + $.t('votes', 'Votes') + ":</td><td></td></tr>");
						var $popoverPercentageTR = $("<tr><td>" + $.t('percentage', 'Percentage') + ":</td><td></td></tr>");
						var $popoverFinishTR = $("<tr><td>" + $.t('finish_height', 'Finish Height') + ":</td><td></td></tr>");
						var $popoverApprovedTR = $("<tr><td>" + $.t('approved', 'Approved') + ":</td><td></td></tr>");

						$popoverTypeTR.appendTo($popoverTable);
						$popoverVotesTR.appendTo($popoverTable);
						$popoverPercentageTR.appendTo($popoverTable);
						$popoverFinishTR.appendTo($popoverTable);
						$popoverApprovedTR.appendTo($popoverTable);

						$popoverPercentageTR.find("td:last").html(percentageFormatted);
						$popoverFinishTR.find("td:last").html(finishHeightFormatted);
						$popoverApprovedTR.find("td:last").html(approvedFormatted);

						var template = '<div class="popover" style="min-width:260px;"><div class="arrow"></div><div class="popover-inner">';
						template += '<h3 class="popover-title"></h3><div class="popover-content"><p></p></div></div></div>';

						var popoverConfig = {
							"html": true,
							"trigger": "hover",
							"placement": "top",
							"template": template
						};

						if (vm == -1) {
							icon = '<i class="fa ion-load-a"></i>';
						}
						if (vm == 0) {
							icon = '<i class="fa fa-group"></i>';
						}
						if (vm == 1) {
							icon = '<i class="fa fa-money"></i>';
						}
						if (vm == 4) {
							icon = '<i class="fa fa-signal"></i>';
						}
						if (vm == 3) {
							icon = '<i class="fa fa-bank"></i>';
						}
						if (vm == 2) {
							icon = '<i class="fa fa-thumbs-up"></i>';
						}
						if (vm == 5) {
							icon = '<i class="fa fa-question"></i>';
						}
						var phasingDiv = "";
						phasingDiv += '<div class="show_popover" style="display:inline-block;min-width:94px;text-align:left;border:1px solid #e2e2e2;background-color:#fff;padding:3px;" ';
	 				 	phasingDiv += 'data-toggle="popover" data-container="body">';
						phasingDiv += "<div class='label label-" + state + "' style='display:inline-block;margin-right:5px;'>" + icon + "</div>";

						if (vm == -1) {
							phasingDiv += '<span style="color:' + color + '">' + $.t("none") + '</span>';
						} else if (vm == 0) {
							phasingDiv += '<span style="color:' + color + '">' + String(responsePoll.result) + '</span> / <span>' + String(attachment.phasingQuorum) + '</span>';
						} else {
							phasingDiv += '<div class="progress" style="display:inline-block;height:10px;width: 50px;">';
	    					phasingDiv += '<div class="progress-bar progress-bar-' + state + '" role="progressbar" aria-valuenow="' + percentageProgressBar + '" ';
	    					phasingDiv += 'aria-valuemin="0" aria-valuemax="100" style="height:10px;width: ' + progressBarWidth + 'px;">';
	      					phasingDiv += '<span class="sr-only">' + percentageProgressBar + '% Complete</span>';
	    					phasingDiv += '</div>';
	  						phasingDiv += '</div> ';
	  					}
						phasingDiv += "</div>";
						var $phasingDiv = $(phasingDiv);
						popoverConfig["content"] = $popoverTable;
						$phasingDiv.popover(popoverConfig);
						$phasingDiv.appendTo($tdPhasing);
                        var votesFormatted;
						if (vm == 0) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = String(responsePoll.result) + " / " + String(attachment.phasingQuorum);
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (vm == 1) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = NRS.convertToBENED(responsePoll.result) + " / " + NRS.convertToBENED(attachment.phasingQuorum) + " BENED";
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (mbModel == 1) {
							if (minBalance > 0) {
								minBalanceFormatted = NRS.convertToBENED(minBalance) + " BENED";
								$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
							}
						}
						if (vm == 2 || mbModel == 2) {
							NRS.sendRequest("getAsset", {
								"asset": attachment.phasingHolding
							}, function(phResponse) {
								if (phResponse && phResponse.asset) {
									if (vm == 2) {
										$popoverTypeTR.find("td:first").html($.t('asset', 'Asset') + ":");
										$popoverTypeTR.find("td:last").html(String(phResponse.name));
										var votesFormatted = NRS.convertToQNTf(responsePoll.result, phResponse.decimals) + " / ";
										votesFormatted += NRS.convertToQNTf(attachment.phasingQuorum, phResponse.decimals) + " QNT";
										$popoverVotesTR.find("td:last").html(votesFormatted);
									}
									if (mbModel == 2) {
										if (minBalance > 0) {
											minBalanceFormatted = NRS.convertToQNTf(minBalance, phResponse.decimals) + " QNT (" + phResponse.name + ")";
											$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
										}
									}
								}
							}, false);
						}
						if (vm == 3 || mbModel == 3) {
							NRS.sendRequest("getCurrency", {
								"currency": attachment.phasingHolding
							}, function(phResponse) {
								if (phResponse && phResponse.currency) {
									if (vm == 3) {
										$popoverTypeTR.find("td:first").html($.t('currency', 'Currency') + ":");
										$popoverTypeTR.find("td:last").html(String(phResponse.code));
										var votesFormatted = NRS.convertToQNTf(responsePoll.result, phResponse.decimals) + " / ";
										votesFormatted += NRS.convertToQNTf(attachment.phasingQuorum, phResponse.decimals) + " Units";
										$popoverVotesTR.find("td:last").html(votesFormatted);
									}
									if (mbModel == 3) {
										if (minBalance > 0) {
											minBalanceFormatted = NRS.convertToQNTf(minBalance, phResponse.decimals) + " Units (" + phResponse.code + ")";
											$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
										}
									}
								}
							}, false);
						}
					});
				} else {
					$tdPhasing.html("&nbsp;");
				}
			}, false);
		} else {
			$tdPhasing.html("&nbsp;");
		}
	};

	NRS.addPhasingInfoToTransactionRows = function(transactions) {
		for (var i = 0; i < transactions.length; i++) {
			var transaction = transactions[i];
			NRS.addPhasedTransactionHTML(transaction);
		}
	};

  NRS.getTransactionRowHTML = function(t, actions, decimals) {
		var transactionType = $.t(NRS.transactionTypes[t.type]['subTypes'][t.subtype]['i18nKeyTitle']);

		if (t.type == 1 && t.subtype == 6 && t.attachment.priceNQT == "0") {
			if (t.sender == NRS.account && t.recipient == NRS.account) {
				transactionType = $.t("alias_sale_cancellation");
			} else {
				transactionType = $.t("alias_transfer");
			}
		}

		var amount = "";
		var sign = 0;
		var fee = new BigInteger(t.feeNQT);
		var feeColor = "";
		var receiving = t.recipient == NRS.account && !(t.sender == NRS.account);
		if (receiving) {
			if (t.amountNQT != "0") {
				amount = new BigInteger(t.amountNQT);
				sign = 1;
			}
			feeColor = "color:black;";
		} else {
			if (t.sender != t.recipient) {
				if (t.amountNQT != "0") {
					amount = new BigInteger(t.amountNQT);
					amount = amount.negate();
					sign = -1;
				}
			} else {
				if (t.amountNQT != "0") {
					amount = new BigInteger(t.amountNQT); // send to myself
				}
			}
			feeColor = "color:red;";
		}
		var formattedAmount = "";
		if (amount != "") {
			formattedAmount = NRS.formatAmount(amount, false, false, decimals.amount);
		}
		var formattedFee = NRS.formatAmount(fee, false, false, decimals.fee);
		var amountColor = (sign == 1 ? "color:green;" : (sign == -1 ? "color:red;" : "color:black;"));
		var hasMessage = false;

		if (t.attachment) {
			if (t.attachment.encryptedMessage || t.attachment.message) {
				hasMessage = true;
			} else if (t.sender == NRS.account && t.attachment.encryptToSelfMessage) {
				hasMessage = true;
			}
		}
		var html = "";
		html += "<tr class='test tr_transaction_" + t.transaction + "'>";
		html += "<td style='vertical-align:middle;'>";
  		html += "<a class='show_transaction_modal_action' href='#' data-timestamp='" + String(t.timestamp).escapeHTML() + "' ";
  		html += "data-transaction='" + String(t.transaction).escapeHTML() + "'>";
  		html += NRS.formatTimestamp(t.timestamp) + "</a>";
  		html += "</td>";
		
      if (NRS.compactTables != 1)
        		html += "<td style='vertical-align:middle;text-align:center;'>" + (hasMessage ? "&nbsp; <i class='fa fa-envelope-o'></i>&nbsp;" : "&nbsp;") + "</td>";
		html += '<td style="vertical-align:middle;">';
		html += NRS.getTransactionIconHTML(t.type, t.subtype, t.senderRS) + '&nbsp; ';
    if (NRS.compactTables != 1)
    		html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + transactionType + '</span>';
		html += '</td>';
        html += "<td style='vertical-align:middle;text-align:right;" + amountColor + "'>" + formattedAmount + "</td>";
        html += "<td style='vertical-align:middle;text-align:right;" + feeColor + "'>" + formattedFee + "</td>";
		
		html += "<td style='vertical-align:middle;'>" + ((NRS.getAccountLink(t, "sender") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "sender")) + " ";
		html += "<i class='fa fa-arrow-circle-right' style='color:#777;'></i> " + ((NRS.getAccountLink(t, "recipient") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "recipient")) + "</td>";
		
		html += "<td style='vertical-align:middle;text-align:center;'>" + NRS.getBlockLink(t.height, null, true) + "</td>";
    if (NRS.compactTables != 1) {
  		html += "<td class='confirmations' style='vertical-align:middle;text-align:center;font-size:12px;'>";
  		html += "<span class='show_popover' data-content='" + (t.confirmed ? NRS.formatAmount(t.confirmations) + " " + $.t("confirmations") : $.t("unconfirmed_transaction")) + "' ";
  		html += "data-container='body' data-placement='left'>";
  		html += (!t.confirmed ? "-" : (t.confirmations > 1440 ? (NRS.formatAmount('144000') + "+") : NRS.formatAmount(t.confirmations))) + "</span></td>";
    }
		if (actions && actions.length != undefined) {
			html += '<td class="td_transaction_actions" style="vertical-align:middle;text-align:right;">';
			if (actions.indexOf('approve') > -1) {
                html += "<a class='btn btn-xs btn-default approve_transaction_btn' href='#' data-toggle='modal' data-target='#approve_transaction_modal' ";
				html += "data-transaction='" + String(t.transaction).escapeHTML() + "' data-fullhash='" + String(t.fullHash).escapeHTML() + "' ";
				html += "data-timestamp='" + t.timestamp + "' " + "data-votingmodel='" + t.attachment.phasingVotingModel + "' ";
				html += "data-fee='1' data-min-balance-formatted=''>" + $.t('approve') + "</a>";
			}
			html += "</td>";
		}
		html += "</tr>";

		var html = '';
		html += '<div class="item">';
		
		// Info
		html += '<div class="info d-flex justify-content-between align-items-center">';
		
		html += '<div class="left d-flex align-items-center">';
		html += '<div class="date"><a class="show_transaction_modal_action" href="#" data-timestamp=' + String(t.timestamp).escapeHTML() + ' data-transaction="' + String(t.transaction).escapeHTML() + '">' + NRS.formatTimestamp(t.timestamp) + '</a></div>';
		html += '<div class="transaction-status ' + transactionType.toLowerCase() + '"></div>';
		html += '<div class="status" style="' + amountColor + ';">' + formattedAmount + '</div>';
		html += '</div>';

		html += '<div class="right d-flex justify-content-end align-items-center">';
		html += '<div class="who">' + ((NRS.getAccountLink(t, "sender") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "sender")) + '</div>';
		html += '<div class="direction from"></div>';
		html += '<div class="wallet">'+ ((NRS.getAccountLink(t, "recipient") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "recipient")) +'</div>';
		
		html += '<button class="arrow down"></button>';
		html += '</div>';

		html += '</div>';

		// Value
		html += '<div class="value"><div class="wrapper d-flex justify-content-between align-items-start">';
		
		// Value Left
		html += '<div class="left">'
		
		html += '<div class="from d-flex justify-content-between align-items-start">'
		html += '<div class="title" data-i18n="from">From</div>';
		html += '<div class="wallet">' + ((NRS.getAccountLink(t, "sender") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "sender")) + '</div>';
		html += '<button class="copy-clipboard from"></button>';
		html += '</div>';

		html +='<div class="to d-flex justify-content-between align-items-start">';
		html += '<div class="title" data-i18n="to">To</div>';
		html += '<div class="wallet">' + ((NRS.getAccountLink(t, "recipient") == "/" && t.type == 2) ? "Asset Exchange" : NRS.getAccountLink(t, "recipient")) + '</div>';
		html += '<button class="copy-clipboard to"></button>';
		html += '</div>';

		html += '<div class="bottom d-flex justify-content-between">';
		html += '<div class="type"><span class="title" data-i18n="type">Type</span> <span>' + transactionType + '</span></div>';
		html += '<div class="type"><span class="title" data-i18n="fee">Fee</span> <span>' + formattedFee + ' BND</span></div>';
		html += '</div>';
		
		html += '</div>'; //End of Value Left

		// Value right
		html += '<div class="right">';
		html += '<div class="height d-flex align-items-start justify-content-between"><i data-i18n="height">Height</i> <span>' + NRS.getBlockLink(t.height, null, true) + '</span></div>';
		html += '<div class="confirmations d-flex align-items-start justify-content-between"><i data-i18n="confirmations">Confirmations</i> <span>'+ (!t.confirmed ? "-" : (t.confirmations > 1440 ? (NRS.formatAmount('144000') + "+") : NRS.formatAmount(t.confirmations))) + '</span> <button class="refresh"></button></div>';
		html += '<div class="id d-flex align-items-start justify-content-between">ID <span>'+ String(t.transaction).escapeHTML() +'</span> <button class="copy-clipboard trans-id"></button></div>';
		html += '</div>';


		html += '</div></div>';

		html += '</div>';

		return html;

		
	};

    NRS.getLedgerEntryRow = function(entry, decimalParams) {
        var linkClass;
        var dataToken;
        if (entry.isTransactionEvent) {
            linkClass = "show_transaction_modal_action";
            dataToken = "data-transaction='" + String(entry.event).escapeHTML() + "'";
        } else {
            linkClass = "show_block_modal_action";
            dataToken = "data-id='1' data-block='" + String(entry.event).escapeHTML()+ "'";
        }
        var change = entry.change;
        var balance = entry.balance;
        var balanceType = "techcoin";
        var balanceEntity = "BENED";
        var holdingIcon = "";
        if (change < 0) {
            change = String(change).substring(1);
        }
        change = NRS.formatAmount(change, false, false, decimalParams.changeDecimals);
        balance = NRS.formatAmount(balance, false, false, decimalParams.balanceDecimals);
        var sign = "";
		var color = "";
        if (entry.change > 0) {
			color = "color:green;";
		} else if (entry.change < 0) {
			color = "color:red;";
			sign = "-";
        }
        var eventType = String(entry.eventType).escapeHTML();
        if (eventType.indexOf("ASSET") == 0 || eventType.indexOf("CURRENCY") == 0) {
            eventType = eventType.substring(eventType.indexOf("_") + 1);
        }
        eventType = $.t(eventType.toLowerCase());
        var html = "";
		html += "<tr>";
		html += "<td style='vertical-align:middle;'>";
  		html += "<a class='show_ledger_modal_action' href='#' data-entry='" + String(entry.ledgerId).escapeHTML() +"'";
        html += "data-change='" + (entry.change < 0 ? ("-" + change) : change) + "' data-balance='" + balance + "'>";
  		html += NRS.formatTimestamp(entry.timestamp) + "</a>";
  		html += "</td>";
		html += '<td style="vertical-align:middle;">';
        html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + eventType + '</span>';
        html += "<a class='" + linkClass + "' href='#' data-timestamp='" + String(entry.timestamp).escapeHTML() + "' " + dataToken + ">";
        html += " <i class='fa fa-info'></i></a>";
		html += '</td>';
		if (balanceType == "techcoin") {
            html += "<td style='vertical-align:middle;" + color + "' class='numeric'>" + sign + change + "</td>";
            html += "<td style='vertical-align:middle;' class='numeric'>" + balance + "</td>";
        } else {
            html += "<td></td>";
            html += "<td></td>";
            html += "<td>" + holdingIcon + balanceEntity + "</td>";
        }
		return html;
	};

	NRS.buildTransactionsTypeNavi = function() {
		var html = '';
		html += '<li role="presentation" class="active"><a href="#" data-transaction-type="" ';
		html += 'data-placement="top" data-content="All" data-container="body" data-i18n="[data-content]all">';
		html += '<span data-i18n="all">All</span></a></li>';
        var typeNavi = $('#transactions_type_navi');
        typeNavi.append(html);

		$.each(NRS.transactionTypes, function(typeIndex, typeDict) {
			var titleString = $.t(typeDict.i18nKeyTitle);
			html = '<li role="presentation"><a href="#" data-transaction-type="' + typeIndex + '" ';
			html += 'data-placement="top" data-content="' + titleString + '" data-container="body">';
			html += titleString + '</a></li>';
if(typeIndex == 0 || typeIndex == 1  )	$('#transactions_type_navi').append(html);
		});

		html  = '<li role="presentation"><a href="#" data-transaction-type="unconfirmed" ';
		html += 'data-placement="top" data-content="Unconfirmed (Account)" data-container="body" data-i18n="[data-content]unconfirmed_account">';
		html += '<span data-i18n="unconfirmed">Unconfirmed</span></a></li>';
		typeNavi.append(html);

		html  = '<li role="presentation"><a href="#" data-transaction-type="all_unconfirmed" ';
		html += 'data-placement="top" data-content="Unconfirmed (Everyone)" data-container="body" data-i18n="[data-content]unconfirmed_everyone">';
		html += '<span data-i18n="all_unconfirmed">Unconfirmed (Everyone)</span></a></li>';
		typeNavi.append(html);

        typeNavi.find('a[data-toggle="popover"]').popover({
			"trigger": "hover"
		});
        typeNavi.find("[data-i18n]").i18n();
	};

	NRS.buildTransactionsSubTypeNavi = function() {
        var subtypeNavi = $('#transactions_sub_type_navi');
        subtypeNavi.empty();
		var html  = '<li role="presentation" class="active"><a href="#" data-transaction-sub-type="">';
		html += '<span>' + $.t("all_types") + '</span></a></li>';
		subtypeNavi.append(html);

		var typeIndex = $('#transactions_type_navi').find('li.active a').attr('data-transaction-type');
		if (typeIndex && typeIndex != "unconfirmed" && typeIndex != "all_unconfirmed" && typeIndex != "phasing") {
			var typeDict = NRS.transactionTypes[typeIndex];
			$.each(typeDict["subTypes"], function(subTypeIndex, subTypeDict) {
				var subTitleString = $.t(subTypeDict.i18nKeyTitle);
				html = '<li role="presentation"><a href="#" data-transaction-sub-type="' + subTypeIndex + '">';
				html += subTypeDict.iconHTML + ' ' + subTitleString + '</a></li>';
				$('#transactions_sub_type_navi').append(html);
			});
		}
	};

    NRS.displayUnconfirmedTransactions = function(account) {
        var params = {
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        };
        if (account != "") {
            params["account"] = account;
        }
        NRS.sendRequest("getUnconfirmedTransactions", params, function(response) {
			var rows = "";
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.unconfirmedTransactions);
				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
                    rows += NRS.getTransactionRowHTML(response.unconfirmedTransactions[i], false, decimals);
				}
			}
			NRS.dataItemsLoaded(rows);
		});
	};

	NRS.displayPhasedTransactions = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
		NRS.sendRequest("getAccountPhasedTransactions", params, function(response) {
			var rows = "";

			if (response.transactions && response.transactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += NRS.getTransactionRowHTML(t, false, decimals);
				}
				NRS.dataItemsLoaded(rows);
				NRS.addPhasingInfoToTransactionRows(response.transactions);
			} else {
				NRS.dataItemsLoaded(rows);
			}

		});
	};

    NRS.pages.dashboard = function() {
        var rows = "";
        var params = {
            "account": NRS.account,
            "firstIndex": 0,
            "lastIndex": 9
        };
        var unconfirmedTransactions = NRS.unconfirmedTransactions;
		var decimals = NRS.getTransactionsAmountDecimals(unconfirmedTransactions);
        if (unconfirmedTransactions) {
            for (var i = 0; i < unconfirmedTransactions.length; i++) {
                rows += NRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
            }
        }

        NRS.sendRequest("getBlockchainTransactions+", params, function(response) {
            if (response.transactions && response.transactions.length) {
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
                for (var i = 0; i < response.transactions.length; i++) {
                    var transaction = response.transactions[i];
                    transaction.confirmed = true;
                    rows += NRS.getTransactionRowHTML(transaction, false, decimals);
                }

                NRS.dataItemsLoaded(rows);
                NRS.addPhasingInfoToTransactionRows(response.transactions);
            } else {
                NRS.dataItemsLoaded(rows);
            }
        });

		NRS.sendRequest("getAccountBlocks+", {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		}, function(response) {
			if (response.blocks && response.blocks.length) {
				if (response.blocks.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.blocks.pop();
				}
				$('#dashboard-blocks-lastdate').html("<a href='#' class='test block show_block_modal_action' data-blockid='"+ NRS.state.lastBlock +"'>" + NRS.formatTimestamp(response.blocks[0].timestamp) + "</a>");
				$('#dashboard-blocks-lastheight, .progress-bar .height .value').html("<a href='#' class='test block show_block_modal_action' data-blockid='"+ NRS.state.lastBlock +"'>" + NRS.lastBlockHeight + "</a>");
				// console.log('Blocks 1');
				NRS.blocksPageLoaded(response.blocks);
			} else {
				// console.log('Blocks 2');
				// NRS.blocksPageLoaded([]);
			}
		});

		NRS.sendRequest("getAccountHierarchy", params, function(response) {
			var count = 0;
			var result = [];
			for (var i = 0; i < response.length; i++) {
				result = response[i].split(",");
console.log('co='+count+' res='+result[3]);
				count = parseInt(result[3]) + count;
			}
console.log('co='+count);
			count = NRS.formatStyledAmount(count);
			var countsmall = count.slice(count.length - 7);
			countsmall = countsmall.toString();
			countsmall = countsmall.replace(/\s+/g, "");
			count = count.substring(0, count.length - 8) + "<span style='font-size:12px;'>," + countsmall + "</span>";
			if(count ==="<span style='font-size:12px;'>,0</span>"){count = 0}
			$('#affilated_volume_count').html(count);
			$('#dashboard-partners-count').text(response.length);
		});
		
    };

	// NRS.pages.blocks = function() {
	// 	if (NRS.blocksPageType == "forged_blocks") {
	// 		$("#forged_fees_total_box, #forged_blocks_total_box").show();
	// 		$("#blocks_transactions_per_hour_box, #blocks_generation_time_box").hide();

	// 		NRS.sendRequest("getAccountBlocks+", {
	// 			"account": NRS.account,
	// 			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
	// 			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
	// 		}, function(response) {
	// 			if (response.blocks && response.blocks.length) {
	// 				if (response.blocks.length > NRS.itemsPerPage) {
	// 					NRS.hasMorePages = true;
	// 					response.blocks.pop();
	// 				}
	// 				console.log('Blocks 1');
	// 				// NRS.blocksPageLoaded(response.blocks);
	// 			} else {
	// 				console.log('Blocks 2');
	// 				// NRS.blocksPageLoaded([]);
	// 			}
	// 		});
	// 	} else {
	// 		$("#forged_fees_total_box, #forged_blocks_total_box").hide();
	// 		$("#blocks_transactions_per_hour_box, #blocks_generation_time_box").show();

	// 		NRS.sendRequest("getBlocks+", {
	// 			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
	// 			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
	// 		}, function(response) {
	// 			if (response.blocks && response.blocks.length) {
	// 				if (response.blocks.length > NRS.itemsPerPage) {
	// 					NRS.hasMorePages = true;
	// 					response.blocks.pop();
	// 				}
	// 				console.log('Blocks 3');
	// 				// NRS.blocksPageLoaded(response.blocks);
	// 			} else {
	// 				console.log('Blocks 4');
	// 				// NRS.blocksPageLoaded([]);
	// 			}
	// 		});
	// 	}
	// };

	NRS.incoming.dashboard = function() {
		NRS.loadPage("dashboard");
	};

	var isHoldingEntry = function (entry){
		return /ASSET_BALANCE/i.test(entry.holdingType) || /CURRENCY_BALANCE/i.test(entry.holdingType);
	};

    NRS.getLedgerNumberOfDecimals = function (entries){
		var decimalParams = {};
		decimalParams.changeDecimals = NRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return NRS.formatAmount(entry.change);
		});
		decimalParams.holdingChangeDecimals = NRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return NRS.formatQuantity(entry.change, entry.holdingInfo.decimals);
			}
			return "";
		});
		decimalParams.balanceDecimals = NRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return NRS.formatAmount(entry.balance);
		});
		decimalParams.holdingBalanceDecimals = NRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return NRS.formatQuantity(entry.balance, entry.holdingInfo.decimals);
			}
			return "";
		});
		return decimalParams;
	};

    NRS.pages.ledger = function() {
		var rows = "";
        var params = {
            "account": NRS.account,
            "includeHoldingInfo": true,
            "firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
            "lastIndex": NRS.pageNumber * NRS.itemsPerPage
        };

        NRS.sendRequest("getAccountLedger+", params, function(response) {
            if (response.entries && response.entries.length) {
                if (response.entries.length > NRS.itemsPerPage) {
                    NRS.hasMorePages = true;
                    response.entries.pop();
                }
				var decimalParams = NRS.getLedgerNumberOfDecimals(response.entries);
                for (var i = 0; i < response.entries.length; i++) {
                    var entry = response.entries[i];
                    rows += NRS.getLedgerEntryRow(entry, decimalParams);
                }
            }
            NRS.dataLoaded(rows);
			if (NRS.ledgerTrimKeep > 0) {
				var ledgerMessage = $("#account_ledger_message");
                ledgerMessage.text($.t("account_ledger_message", { blocks: NRS.ledgerTrimKeep }));
				ledgerMessage.show();
			}
        });
	};

	NRS.pages.transactions = function(callback, subpage) {
        var typeNavi = $('#transactions_type_navi');
        if (typeNavi.children().length == 0) {
			NRS.buildTransactionsTypeNavi();
			NRS.buildTransactionsSubTypeNavi();
		}

		if (subpage) {
			typeNavi.find('li a[data-transaction-type="' + subpage + '"]').click();
			return;
		}

		var selectedType = typeNavi.find('li.active a').attr('data-transaction-type');
		var selectedSubType = $('#transactions_sub_type_navi').find('li.active a').attr('data-transaction-sub-type');
		if (!selectedSubType) {
			selectedSubType = "";
		}
		if (selectedType == "unconfirmed") {
			NRS.displayUnconfirmedTransactions(NRS.account);
			return;
		}
		if (selectedType == "phasing") {
			NRS.displayPhasedTransactions();
			return;
		}
		if (selectedType == "all_unconfirmed") {
			NRS.displayUnconfirmedTransactions("");
			return;
		}

		var rows = "";
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
        var unconfirmedTransactions;
		if (selectedType) {
			params.type = selectedType;
			params.subtype = selectedSubType;
			unconfirmedTransactions = NRS.getUnconfirmedTransactionsFromCache(params.type, (params.subtype ? params.subtype : []));
		} else {
			unconfirmedTransactions = NRS.unconfirmedTransactions;
		}
		var decimals = NRS.getTransactionsAmountDecimals(unconfirmedTransactions);
		if (unconfirmedTransactions) {
			for (var i = 0; i < unconfirmedTransactions.length; i++) {
				rows += NRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
			}
		}

		NRS.sendRequest("getBlockchainTransactions+", params, function(response) {
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					rows += NRS.getTransactionRowHTML(transaction, false, decimals);
				}

				NRS.dataItemsLoaded(rows);
				NRS.addPhasingInfoToTransactionRows(response.transactions);
        // var trxTable = $('#transactions_table');
		var trxTable = $('#transactions_items');
        if (NRS.compactTables == 1) {
          trxTable.find("thead tr").children(":nth-child(2)").hide();
          trxTable.find("thead tr").children(":nth-child(8)").hide();

        } else {
          trxTable.find("thead tr").children(":nth-child(2)").show();
          trxTable.find("thead tr").children(":nth-child(8)").show();

        }
			} else {
				NRS.dataItemsLoaded(rows);
			}
		});
	};

  NRS.pages.hierarchy = function(callback, subpage) {
		var rows = "";
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage
		};

    NRS.sendRequest("getAccountChildren", params, function(response) {
        var infoModalTransactionsTable = $("#user_info_modal_hierarchy_table");
        if (response.children && response.children.length) {
          if (response.children.length > NRS.itemsPerPage) {
  					NRS.hasMorePages = true;
  				}
          var rows = "";
          var amountDecimals = NRS.getNumberOfDecimals(response.children, "amountNQT", function(val) {
            return NRS.formatAmount(val.amountNQT);
          });
          var feeDecimals = NRS.getNumberOfDecimals(response.children, "balanceNQT", function(val) {
            return NRS.formatAmount(val.fee);
          });
          for (var i = 0; i < response.children.length && i < NRS.itemsPerPage; i++) {
            var child = response.children[i];
            // if (child.amountNQT) {
            //   child.amount = new BigInteger(child.amountNQT);
            //   child.balance = new BigInteger(child.balanceNQT);
            // }
            rows += "<tr data-sublevel='0'>" +
              "<td>" +
              (child.childCount>0?"<button type='button' class='btn-dropdown' onclick='NRS.expand(\""+String(child.accountRS)+"\", this)'><i class='fa fa-angle-right fa-lg'></i></button> ":"<button type='button' class='btn-dropdown' style='pointer-events:none'><i class='fa fa-angle-right fa-lg' style='visibility:hidden'></i></button> ") +
              "<a href='#' data-user='" + String(child.accountRS).escapeHTML() + "' id='account_link_in_sidebar' class='show_account_modal_action user-info'>" + (child.name != null?(String(child.name).length>0?"<i class='fa fa-user'></i> "+unescape(child.name):child.accountRS):child.accountRS) + "</a></td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.balanceNQT/1000000, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.amountNQT/1000000, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + (child.childCount>0?child.childCount:"") + "</td>" +
              "<td>" + (child.forging?"+":"") + "</td>" +
            "</tr>";
            child.confirmed = true;
          }

          $("#hierarchy_page > section > div > div").attachDragger();

		  $("#transactions_items .items").empty().append(rows);

        //   infoModalTransactionsTable.find("tbody").empty().append(rows);
          NRS.dataLoaded(rows);
        } else {
			$("#transactions_items .items").empty();
		//   infoModalTransactionsTable.find("tbody").empty();
          NRS.dataLoaded(rows);
        }
    });
	};

  NRS.hierarchyRows = function(accountRS, pageNumber, sublevel, cb) {
    var rows = "";
		var params = {
			"account": accountRS,
			"firstIndex": pageNumber * 100 - 100,
      "lastIndex": pageNumber * 100
		};
    NRS.sendRequest("getAccountChildren", params, function(response) {
        if (response.children && response.children.length) {
          var rows = "";
          var amountDecimals = NRS.getNumberOfDecimals(response.children, "amountNQT", function(val) {
            return NRS.formatAmount(val.amountNQT);
          });
          var feeDecimals = NRS.getNumberOfDecimals(response.children, "balanceNQT", function(val) {
            return NRS.formatAmount(val.fee);
          });
          for (var i = 0; i < response.children.length; i++) {
            var child = response.children[i];
            var spacer = "";
            for (var spacers = 0; spacers < sublevel; spacers++)
              spacer = spacer + "<button type='button' class='hierarchy-spacer "+NRS.nextColor(spacers+1)+"'>&nbsp;</button>";
            rows += "<tr data-sublevel='"+sublevel+"' style='transition:0.3s all linear;opacity:0'>" +
              // "<td style='border-left: "+(sublevel*4)+"px solid red;padding-left: "+(sublevel*20+8)+"px;'>"+
              "<td>"+ spacer +
              (child.childCount>0?"<button type='button' class='btn-dropdown' onclick='NRS.expand(\""+String(child.accountRS)+"\", this)'><i class='fa fa-angle-right fa-lg'></i></button>":"<button type='button' class='btn-dropdown'><i class='fa fa-angle-right fa-lg' style='visibility:hidden'></i></button>")+
              " <a href='#' data-user='" + String(child.accountRS).escapeHTML() + "' id='account_link_in_sidebar' class='show_account_modal_action user-info'>" + (child.name != null?(String(child.name).length>0?"<i class='fa fa-user'></i> "+unescape(child.name):child.accountRS):child.accountRS) + "</a></td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.balanceNQT/1000000, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + NRS.formatAmount(child.amountNQT/1000000, false, false, amountDecimals) + "</td>" +
              "<td class='numeric'> " + (child.childCount>0?child.childCount:"") + "</td>" +
              "<td>" + (child.forging?"+":"") + "</td>" +
            "</tr>";
            child.confirmed = true;
          }
          cb(rows);
        }
    });
  }

  NRS.expand = function(accountRS, row) {
    var t = $("#user_info_modal_hierarchy_table");
    var tr = $(row).closest("tr");
    var icon = $(row).children("i");
    var sublevel = tr.data("sublevel") + 1;
    var button = tr.children("td:first-child").find("button.btn-dropdown");
    t.removeClass("dropdown-upround")
    let toremove = [];
    tr.siblings().each(function(){
      sib = $(this);
      var sibicon = sib.children("td:first-child").find("i").first();
      var sibbutton = sib.children("td:first-child").find("button.btn-dropdown");
      if (sib.attr("data-sublevel") >= sublevel) {
        sib.css("opacity", "0");
        sib.removeClass("expanded");
        sib.children("td").children(".btn-dropdown").removeClass("color1");
        sib.children("td").children(".btn-dropdown").removeClass("color2");
        sib.children("td").children(".btn-dropdown").removeClass("color3");
        sib.children("td").children(".btn-dropdown").removeClass("color4");
        sib.data("dying", true);
        toremove.push(sib);
      } else if (sib.attr("data-sublevel") == sublevel-1) {
        if (sibicon.hasClass("fa-angle-down")) {
          sibicon.removeClass("fa-angle-down");
          sibicon.addClass("fa-angle-right");
          sibbutton.removeClass("color1");
          sibbutton.removeClass("color2");
          sibbutton.removeClass("color3");
          sibbutton.removeClass("color4");
        }
        sib.removeClass("dropdown-upround");
      }
    });
    if (toremove && toremove.length > 0) {
      setTimeout(function(){
        var i;
        for (i = 0; i < toremove.length; i++) {
          $(toremove[i]).remove();
        }
      },200);
    }
    if (icon.hasClass("fa-angle-right")) {
      icon.removeClass("fa-angle-right");
      icon.addClass("fa-angle-down");
      tr.addClass("expanded");
      $(row).addClass(NRS.nextColor(sublevel));
      NRS.hierarchyRows(accountRS, 1, sublevel, function(rows){
        var next = tr.next();
        tr.after(rows);
        next.addClass("dropdown-upround");
        setTimeout(function(){
          tr.siblings().each(function(){
            sib = $(this);
            var sibicon = sib.children("td:first-child").find("i");
            if (sibicon.hasClass("fa-angle-down")) {
              sib.addClass("expanded");
            } else {
              sib.removeClass("expanded");
            }
            var siblevel = sib.attr("data-sublevel");
            if (siblevel == sublevel && sib.data("dying") != true) {
              // sib.children("td").children(".btn-dropdown").css("margin-left", (sublevel*20)+"px")
            }
            sib.css("opacity", "1");
          });
        }, 50);

      });
    } else {
      icon.removeClass("fa-angle-down");
      icon.addClass("fa-angle-right");
      tr.removeClass("expanded");
      button.css("background", "none");
      button.removeClass("color1");
      button.removeClass("color2");
      button.removeClass("color3");
      button.removeClass("color4");
    }

    var headForging = $("#hierarchy_table>thead>tr>th:last-child");
    var headDisciples = $("#hierarchy_table>thead>tr>th:nth-child(4)");
    if (sublevel > 0 && !NRS.isHierarchyTableMinified) {
      // Minify
      NRS.isHierarchyTableMinified = true;
      headForging.html("FRG");
      headDisciples.html("#");
    } else if (sublevel == 1 && NRS.isHierarchyTableMinified) {
      // Cancel minification
      NRS.isHierarchyTableMinified = false;
      headForging.html($.t("forging"));
      headDisciples.html($.t("disciples"));
    }
  }

	NRS.updateApprovalRequests = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 20
		};
		NRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var $badge = $('#dashboard_link').find('.sm_treeview_submenu a[data-page="approval_requests_account"] span.badge');
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length == 0) {
					$badge.hide();
				} else {
                    var length;
					if (response.transactions.length == 21) {
						length = "20+";
					} else {
						length = String(response.transactions.length);
					}
					$badge.text(length);
					$badge.show();
				}
			} else {
				$badge.hide();
			}
		});
		if (NRS.currentPage == 'approval_requests_account') {
			NRS.loadPage(NRS.currentPage);
		}
	};

	NRS.pages.approval_requests_account = function() {
		var params = {
			"account": NRS.account,
			"firstIndex": NRS.pageNumber * NRS.itemsPerPage - NRS.itemsPerPage,
			"lastIndex": NRS.pageNumber * NRS.itemsPerPage
		};
		NRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var rows = "";

			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > NRS.itemsPerPage) {
					NRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = NRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += NRS.getTransactionRowHTML(t, ['approve'], decimals);
				}
			}
			NRS.dataItemsLoaded(rows);
			NRS.addPhasingInfoToTransactionRows(response.transactions);
		});
	};

	NRS.incoming.transactions = function() {
		NRS.loadPage("transactions");
	};

	NRS.setup.transactions = function() {
		var sidebarId = 'dashboard_link';


		var options = {
			"id": 'account_info_button',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 19 19" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12.3525 0.113814C12.3143 0.114753 12.2762 0.118493 12.2385 0.125004H2.98966C1.41686 0.125004 0.125079 1.41679 0.125079 2.98959V12.2364C0.111302 12.3203 0.111302 12.4058 0.125079 12.4897V16.0104C0.125079 17.5832 1.41686 18.875 2.98966 18.875H12.2365C12.3204 18.8888 12.4059 18.8888 12.4898 18.875H16.0105C17.5833 18.875 18.8751 17.5832 18.8751 16.0104V12.4928C18.8889 12.4089 18.8889 12.3233 18.8751 12.2395V2.98959C18.8751 1.41679 17.5833 0.125004 16.0105 0.125004H12.4928C12.4465 0.117056 12.3995 0.113311 12.3525 0.113814ZM2.98966 1.6875H5.85425V5.85417H1.68758V2.98959C1.68758 2.26134 2.26142 1.6875 2.98966 1.6875ZM7.41674 1.6875H11.5834V5.85417H7.41674V1.6875ZM13.1459 1.6875H16.0105C16.7387 1.6875 17.3126 2.26134 17.3126 2.98959V5.85417H13.1459V1.6875ZM1.68758 7.41667H5.85425V11.5833H1.68758V7.41667ZM7.41674 7.41667H11.5834V11.5833H7.41674V7.41667ZM13.1459 7.41667H17.3126V11.5833H13.1459V7.41667ZM1.68758 13.1458H5.85425V17.3125H2.98966C2.26142 17.3125 1.68758 16.7387 1.68758 16.0104V13.1458ZM7.41674 13.1458H11.5834V17.3125H7.41674V13.1458ZM13.1459 13.1458H17.3126V16.0104C17.3126 16.7387 16.7387 17.3125 16.0105 17.3125H13.1459V13.1458Z" fill="black"/></svg> <span data-i18n="dashboard">Dashboard</span>',
			"page": 'dashboard',
			"desiredPosition": 10,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'contacts_button_sidebar',
			"titleHTML": '<svg width="21" height="20" viewBox="0 0 23 22" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M5.35273 0.380615C4.29796 0.380615 3.39937 0.805969 2.82273 1.45468C2.2461 2.1034 1.97756 2.93536 1.97756 3.75579C1.97756 4.57621 2.2461 5.40817 2.82273 6.05689C3.39937 6.7056 4.29796 7.13206 5.35273 7.13206C6.4075 7.13206 7.3061 6.7056 7.88274 6.05689C8.45937 5.40817 8.7279 4.57621 8.7279 3.75579C8.7279 2.93536 8.45937 2.1034 7.88274 1.45468C7.3061 0.805969 6.4075 0.380615 5.35273 0.380615ZM17.1851 0.380615C16.1303 0.380615 15.2317 0.805969 14.6551 1.45468C14.0785 2.1034 13.8099 2.93536 13.8099 3.75579C13.8099 4.57621 14.0785 5.40817 14.6551 6.05689C15.2317 6.7056 16.1303 7.13206 17.1851 7.13206C18.2399 7.13206 19.1385 6.7056 19.7151 6.05689C20.2917 5.40817 20.5603 4.57621 20.5603 3.75579C20.5603 2.93536 20.2917 2.1034 19.7151 1.45468C19.1385 0.805969 18.2399 0.380615 17.1851 0.380615ZM5.35273 2.07095C5.98492 2.07095 6.35254 2.27808 6.61938 2.57827C6.88623 2.87847 7.03757 3.31099 7.03757 3.75579C7.03757 4.20058 6.88623 4.6342 6.61938 4.9344C6.35254 5.2346 5.98492 5.44172 5.35273 5.44172C4.72055 5.44172 4.35292 5.2346 4.08608 4.9344C3.81924 4.6342 3.6679 4.20058 3.6679 3.75579C3.6679 3.31099 3.81924 2.87847 4.08608 2.57827C4.35292 2.27808 4.72055 2.07095 5.35273 2.07095ZM17.1851 2.07095C17.8173 2.07095 18.1849 2.27808 18.4517 2.57827C18.7186 2.87847 18.8699 3.31099 18.8699 3.75579C18.8699 4.20058 18.7186 4.6342 18.4517 4.9344C18.1849 5.2346 17.8173 5.44172 17.1851 5.44172C16.5529 5.44172 16.1853 5.2346 15.9184 4.9344C15.6516 4.6342 15.5003 4.20058 15.5003 3.75579C15.5003 3.31099 15.6516 2.87847 15.9184 2.57827C16.1853 2.27808 16.5529 2.07095 17.1851 2.07095ZM1.8466 8.25895C0.841742 8.25895 0 9.08555 0 10.0945V10.2189C0 11.0287 0.326133 11.8168 0.929905 12.3824C1.71019 13.1139 3.13248 13.9033 5.35273 13.9033C7.57298 13.9033 8.99545 13.1142 9.77556 12.3835C10.3796 11.8176 10.7055 11.0287 10.7055 10.2189V10.0945C10.7055 9.08555 9.86372 8.25895 8.85886 8.25895H1.8466ZM13.679 8.25895C12.6741 8.25895 11.8324 9.08555 11.8324 10.0945V10.2178C11.8324 11.0276 12.1582 11.8165 12.7623 12.3824C13.5425 13.1139 14.9648 13.9033 17.1851 13.9033C19.4053 13.9033 20.8278 13.1142 21.6079 12.3835C22.212 11.8176 22.5378 11.0287 22.5378 10.2189V10.0945C22.5378 9.08555 21.6961 8.25895 20.6912 8.25895H13.679ZM1.8466 9.94929H8.85886C8.96061 9.94929 9.01513 10.0093 9.01513 10.0945V10.2189C9.01513 10.5777 8.87356 10.9124 8.62006 11.1499C8.12115 11.6172 7.18817 12.213 5.35273 12.213C3.5173 12.213 2.58415 11.6175 2.08541 11.1499C1.83162 10.9121 1.69034 10.5777 1.69034 10.2189V10.0945C1.69034 10.0093 1.74486 9.94929 1.8466 9.94929ZM13.679 9.94929H20.6912C20.793 9.94929 20.8475 10.0093 20.8475 10.0945V10.2189C20.8475 10.5777 20.7059 10.9124 20.4524 11.1499C19.9535 11.6172 19.0205 12.213 17.1851 12.213C15.3497 12.213 14.4165 11.6175 13.9178 11.1499C13.6643 10.9124 13.5227 10.5766 13.5227 10.2178V10.0945C13.5227 10.0093 13.5772 9.94929 13.679 9.94929ZM18.8666 15.0214C18.6984 15.0214 18.5341 15.0717 18.3946 15.1656C18.2551 15.2596 18.1468 15.3931 18.0836 15.5489C18.0203 15.7048 18.0051 15.876 18.0397 16.0406C18.0743 16.2052 18.1572 16.3557 18.2779 16.4729L19.0889 17.284H13.8044C13.6924 17.2824 13.5812 17.3031 13.4773 17.3449C13.3734 17.3866 13.2788 17.4486 13.199 17.5273C13.1193 17.6059 13.0559 17.6996 13.0127 17.8029C12.9695 17.9063 12.9472 18.0171 12.9472 18.1292C12.9472 18.2412 12.9695 18.352 13.0127 18.4554C13.0559 18.5587 13.1193 18.6524 13.199 18.731C13.2788 18.8097 13.3734 18.8717 13.4773 18.9134C13.5812 18.9552 13.6924 18.9759 13.8044 18.9743H19.0889L18.2779 19.7854C18.1968 19.8633 18.132 19.9565 18.0874 20.0598C18.0428 20.163 18.0192 20.2741 18.0181 20.3865C18.0169 20.4989 18.0382 20.6105 18.0807 20.7146C18.1232 20.8187 18.1861 20.9133 18.2656 20.9928C18.3451 21.0723 18.4397 21.1351 18.5438 21.1776C18.6479 21.2201 18.7594 21.2414 18.8719 21.2403C18.9843 21.2391 19.0954 21.2156 19.1986 21.171C19.3018 21.1264 19.3951 21.0616 19.473 20.9805L21.7268 18.7267C21.8852 18.5682 21.9742 18.3533 21.9742 18.1292C21.9742 17.905 21.8852 17.6901 21.7268 17.5316L19.473 15.2778C19.3942 15.1967 19.2999 15.1322 19.1958 15.0881C19.0916 15.0441 18.9797 15.0214 18.8666 15.0214ZM3.64589 15.0225C3.42639 15.0289 3.218 15.1205 3.06484 15.2778L0.811053 17.5316C0.652613 17.6901 0.563608 17.905 0.563608 18.1292C0.563608 18.3533 0.652613 18.5682 0.811053 18.7267L3.06484 20.9805C3.14271 21.0616 3.23599 21.1264 3.33921 21.171C3.44243 21.2156 3.55351 21.2392 3.66596 21.2403C3.7784 21.2414 3.88993 21.2201 3.99404 21.1776C4.09814 21.1351 4.19272 21.0723 4.27223 20.9928C4.35174 20.9133 4.41459 20.8187 4.45709 20.7146C4.4996 20.6105 4.5209 20.4989 4.51976 20.3865C4.51861 20.2741 4.49505 20.163 4.45044 20.0598C4.40583 19.9565 4.34107 19.8633 4.25996 19.7854L3.4489 18.9743H8.73341C8.8454 18.9759 8.95659 18.9552 9.06052 18.9134C9.16445 18.8717 9.25904 18.8097 9.3388 18.731C9.41856 18.6524 9.48189 18.5587 9.52512 18.4554C9.56835 18.352 9.59061 18.2412 9.59061 18.1292C9.59061 18.0171 9.56835 17.9063 9.52512 17.8029C9.48189 17.6996 9.41856 17.6059 9.3388 17.5273C9.25904 17.4486 9.16445 17.3866 9.06052 17.3449C8.95659 17.3031 8.8454 17.2824 8.73341 17.284H3.4489L4.25996 16.4729C4.38189 16.3542 4.46513 16.2014 4.49878 16.0345C4.53244 15.8677 4.51495 15.6946 4.44859 15.5379C4.38224 15.3811 4.27011 15.2481 4.12689 15.1561C3.98366 15.0642 3.81602 15.0176 3.64589 15.0225Z" fill="black"/></svg><span data-i18n="contacts">Contacts</span>',
			"page": 'contacts',
			"desiredPosition": 14,
		};
		NRS.addSimpleSidebarMenuItem(options);

		//NRS.addSpacerToSidebar("Account", 16);

		options = {
			"id": 'account_ledger_button',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 19 19" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M2.98958 0.125C1.41678 0.125 0.125 1.41678 0.125 2.98958V16.0104C0.125 17.5832 1.41678 18.875 2.98958 18.875H16.0104C17.5832 18.875 18.875 17.5832 18.875 16.0104V2.98958C18.875 1.41678 17.5832 0.125 16.0104 0.125H2.98958ZM2.98958 1.6875H16.0104C16.7387 1.6875 17.3125 2.26134 17.3125 2.98958V16.0104C17.3125 16.7387 16.7387 17.3125 16.0104 17.3125H2.98958C2.26134 17.3125 1.6875 16.7387 1.6875 16.0104V2.98958C1.6875 2.26134 2.26134 1.6875 2.98958 1.6875ZM4.55208 4.8125C4.44856 4.81104 4.34577 4.83016 4.24971 4.86877C4.15364 4.90737 4.0662 4.96468 3.99247 5.03737C3.91875 5.11007 3.8602 5.19668 3.82024 5.2922C3.78028 5.38771 3.75971 5.49021 3.75971 5.59375C3.75971 5.69729 3.78028 5.79979 3.82024 5.8953C3.8602 5.99082 3.91875 6.07743 3.99247 6.15012C4.0662 6.22282 4.15364 6.28013 4.24971 6.31873C4.34577 6.35734 4.44856 6.37646 4.55208 6.375H14.4479C14.5514 6.37646 14.6542 6.35734 14.7503 6.31873C14.8464 6.28013 14.9338 6.22282 15.0075 6.15012C15.0813 6.07743 15.1398 5.99082 15.1798 5.8953C15.2197 5.79979 15.2403 5.69729 15.2403 5.59375C15.2403 5.49021 15.2197 5.38771 15.1798 5.2922C15.1398 5.19668 15.0813 5.11007 15.0075 5.03737C14.9338 4.96468 14.8464 4.90737 14.7503 4.86877C14.6542 4.83016 14.5514 4.81104 14.4479 4.8125H4.55208ZM4.55208 8.71875C4.44856 8.71729 4.34577 8.73641 4.24971 8.77502C4.15364 8.81362 4.0662 8.87093 3.99247 8.94363C3.91875 9.01632 3.8602 9.10293 3.82024 9.19845C3.78028 9.29396 3.75971 9.39646 3.75971 9.5C3.75971 9.60353 3.78028 9.70604 3.82024 9.80155C3.8602 9.89707 3.91875 9.98368 3.99247 10.0564C4.0662 10.1291 4.15364 10.1864 4.24971 10.225C4.34577 10.2636 4.44856 10.2827 4.55208 10.2812H14.4479C14.5514 10.2827 14.6542 10.2636 14.7503 10.225C14.8464 10.1864 14.9338 10.1291 15.0075 10.0564C15.0813 9.98368 15.1398 9.89707 15.1798 9.80155C15.2197 9.70604 15.2403 9.60353 15.2403 9.5C15.2403 9.39646 15.2197 9.29396 15.1798 9.19845C15.1398 9.10293 15.0813 9.01632 15.0075 8.94363C14.9338 8.87093 14.8464 8.81362 14.7503 8.77502C14.6542 8.73641 14.5514 8.71729 14.4479 8.71875H4.55208ZM4.55208 12.625C4.44856 12.6235 4.34577 12.6427 4.24971 12.6813C4.15364 12.7199 4.0662 12.7772 3.99247 12.8499C3.91875 12.9226 3.8602 13.0092 3.82024 13.1047C3.78028 13.2002 3.75971 13.3027 3.75971 13.4062C3.75971 13.5098 3.78028 13.6123 3.82024 13.7078C3.8602 13.8033 3.91875 13.8899 3.99247 13.9626C4.0662 14.0353 4.15364 14.0926 4.24971 14.1312C4.34577 14.1698 4.44856 14.189 4.55208 14.1875H14.4479C14.5514 14.189 14.6542 14.1698 14.7503 14.1312C14.8464 14.0926 14.9338 14.0353 15.0075 13.9626C15.0813 13.8899 15.1398 13.8033 15.1798 13.7078C15.2197 13.6123 15.2403 13.5098 15.2403 13.4062C15.2403 13.3027 15.2197 13.2002 15.1798 13.1047C15.1398 13.0092 15.0813 12.9226 15.0075 12.8499C14.9338 12.7772 14.8464 12.7199 14.7503 12.6813C14.6542 12.6427 14.5514 12.6235 14.4479 12.625H4.55208Z" fill="black"/></svg><span data-i18n="account_ledger">Ledger</span>',
			"page": 'ledger',
			"desiredPosition": 15,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'my_transactions_button',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 21 21" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4.23486 0.604111C3.96391 0.608163 3.7052 0.717631 3.51363 0.909287L0.388626 4.03429C0.288654 4.13027 0.208839 4.24524 0.153855 4.37246C0.0988708 4.49967 0.0698231 4.63658 0.0684133 4.77517C0.0670034 4.91375 0.0932597 5.05122 0.145644 5.17953C0.198029 5.30784 0.275489 5.42441 0.373487 5.52241C0.471486 5.62041 0.588053 5.69787 0.716363 5.75025C0.844672 5.80264 0.982144 5.82889 1.12073 5.82748C1.25931 5.82607 1.39622 5.79702 1.52344 5.74204C1.65066 5.68706 1.76562 5.60724 1.86161 5.50727L3.20845 4.16043V13.6249C3.2065 13.763 3.232 13.9 3.28347 14.0281C3.33494 14.1562 3.41136 14.2728 3.50828 14.3711C3.6052 14.4694 3.7207 14.5475 3.84805 14.6007C3.9754 14.654 4.11207 14.6814 4.25012 14.6814C4.38816 14.6814 4.52483 14.654 4.65219 14.6007C4.77954 14.5475 4.89503 14.4694 4.99195 14.3711C5.08887 14.2728 5.16529 14.1562 5.21676 14.0281C5.26823 13.9 5.29374 13.763 5.29178 13.6249V4.16043L6.63863 5.50727C6.73461 5.60724 6.84958 5.68706 6.9768 5.74204C7.10401 5.79703 7.24092 5.82608 7.37951 5.82749C7.51809 5.8289 7.65557 5.80264 7.78388 5.75026C7.91219 5.69787 8.02875 5.62041 8.12675 5.52241C8.22475 5.42442 8.30221 5.30785 8.3546 5.17954C8.40698 5.05123 8.43324 4.91375 8.43183 4.77517C8.43042 4.63658 8.40137 4.49967 8.34638 4.37246C8.2914 4.24524 8.21158 4.13027 8.11161 4.03429L4.98661 0.909287C4.88808 0.810723 4.77079 0.732929 4.64166 0.680509C4.51254 0.62809 4.3742 0.602111 4.23486 0.604111ZM16.7349 0.604111C16.4639 0.608163 16.2052 0.717631 16.0136 0.909287L12.8886 4.03429C12.7887 4.13027 12.7088 4.24524 12.6539 4.37246C12.5989 4.49967 12.5698 4.63658 12.5684 4.77517C12.567 4.91375 12.5933 5.05123 12.6456 5.17954C12.698 5.30785 12.7755 5.42442 12.8735 5.52241C12.9715 5.62041 13.088 5.69787 13.2164 5.75026C13.3447 5.80264 13.4821 5.8289 13.6207 5.82749C13.7593 5.82608 13.8962 5.79703 14.0234 5.74204C14.1507 5.68706 14.2656 5.60724 14.3616 5.50727L15.7084 4.16043V13.6249C15.7065 13.763 15.732 13.9 15.7835 14.0281C15.8349 14.1562 15.9114 14.2728 16.0083 14.3711C16.1052 14.4694 16.2207 14.5475 16.348 14.6007C16.4754 14.654 16.6121 14.6814 16.7501 14.6814C16.8882 14.6814 17.0248 14.654 17.1522 14.6007C17.2795 14.5475 17.395 14.4694 17.492 14.3711C17.5889 14.2728 17.6653 14.1562 17.7168 14.0281C17.7682 13.9 17.7937 13.763 17.7918 13.6249V4.16043L19.1386 5.50727C19.2346 5.60724 19.3496 5.68706 19.4768 5.74204C19.604 5.79703 19.7409 5.82608 19.8795 5.82749C20.0181 5.8289 20.1556 5.80264 20.2839 5.75026C20.4122 5.69787 20.5288 5.62041 20.6268 5.52241C20.7248 5.42442 20.8022 5.30785 20.8546 5.17954C20.907 5.05123 20.9332 4.91375 20.9318 4.77517C20.9304 4.63658 20.9014 4.49967 20.8464 4.37246C20.7914 4.24524 20.7116 4.13027 20.6116 4.03429L17.4866 0.909287C17.3881 0.810723 17.2708 0.732929 17.1417 0.680509C17.0125 0.62809 16.8742 0.602111 16.7349 0.604111ZM10.4849 6.83987C10.2088 6.84392 9.94558 6.9574 9.75312 7.1554C9.56066 7.35339 9.45467 7.61969 9.45845 7.89578V16.8395L8.11161 15.4926C8.01562 15.3926 7.90066 15.3128 7.77344 15.2578C7.64622 15.2029 7.50931 15.1738 7.37073 15.1724C7.23214 15.171 7.09467 15.1972 6.96636 15.2496C6.83805 15.302 6.72148 15.3795 6.62348 15.4775C6.52548 15.5755 6.44802 15.692 6.39564 15.8204C6.34325 15.9487 6.317 16.0861 6.31841 16.2247C6.31982 16.3633 6.34887 16.5002 6.40385 16.6274C6.45884 16.7547 6.53865 16.8696 6.63863 16.9656L9.76363 20.0906C9.95898 20.2859 10.2239 20.3956 10.5001 20.3956C10.7763 20.3956 11.0413 20.2859 11.2366 20.0906L14.3616 16.9656C14.4616 16.8696 14.5414 16.7547 14.5964 16.6274C14.6514 16.5002 14.6804 16.3633 14.6818 16.2247C14.6832 16.0861 14.657 15.9487 14.6046 15.8204C14.5522 15.692 14.4748 15.5755 14.3768 15.4775C14.2788 15.3795 14.1622 15.302 14.0339 15.2496C13.9056 15.1972 13.7681 15.171 13.6295 15.1724C13.4909 15.1738 13.354 15.2029 13.2268 15.2578C13.0996 15.3128 12.9846 15.3926 12.8886 15.4926L11.5418 16.8395V7.89578C11.5437 7.7565 11.5176 7.61826 11.4652 7.48922C11.4127 7.36018 11.3349 7.24298 11.2364 7.14453C11.1379 7.04609 11.0206 6.9684 10.8915 6.91607C10.7624 6.86374 10.6241 6.83783 10.4849 6.83987Z" fill="black"/></svg><span data-i18n="my_transactions">My Transactions</span>',
			"page": 'transactions',
			"desiredPosition": 16,
		};
		NRS.addSimpleSidebarMenuItem(options);




		//NRS.addSpacerToSidebar("Blockchain", 35);

		options = {
			"id": 'blocks_button',
			"titleHTML": '<svg width="21" height="22" viewBox="0 0 21 22" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5001 0.556396C9.72648 0.556397 8.95339 0.737337 8.24585 1.09961C8.24517 1.09995 8.24449 1.10029 8.24381 1.10063L2.15759 4.23376C1.20532 4.7242 0.604248 5.70942 0.604248 6.78096V15.1774C0.604248 16.2486 1.20464 17.2335 2.15759 17.7236L8.24381 20.8577C8.24415 20.858 8.24449 20.8584 8.24483 20.8587C9.65992 21.5833 11.3392 21.5833 12.7543 20.8587C12.755 20.8584 12.7557 20.858 12.7563 20.8577L18.8426 17.7236C19.7948 17.2331 20.3949 16.2486 20.3949 15.1774V6.78096C20.3949 5.71 19.7951 4.72497 18.8426 4.23478H18.8416L12.7563 1.10063C12.756 1.10029 12.7557 1.09995 12.7553 1.09961C12.0478 0.737335 11.2737 0.556396 10.5001 0.556396ZM10.5001 2.1189C11.0291 2.1189 11.5586 2.24307 12.0433 2.49121L18.1264 5.62333C18.1268 5.62367 18.1271 5.62401 18.1274 5.62435C18.562 5.84782 18.8324 6.29177 18.8324 6.78096V15.1774C18.8324 15.6666 18.5617 16.1109 18.1264 16.335L12.0422 19.4671C11.0736 19.9631 9.9278 19.9634 8.95894 19.4681L8.95691 19.4671L2.87272 16.335C2.87272 16.3347 2.87272 16.3343 2.87272 16.334C2.43815 16.1105 2.16675 15.6666 2.16675 15.1774V6.78096C2.16675 6.29105 2.43749 5.84748 2.87272 5.62333L8.95793 2.49121C9.44257 2.24307 9.97108 2.1189 10.5001 2.1189ZM3.97441 6.02616C3.79918 6.02766 3.62954 6.08802 3.49274 6.19754C3.35595 6.30707 3.25995 6.4594 3.22016 6.63005C3.18037 6.80071 3.19911 6.97979 3.27336 7.13852C3.34761 7.29725 3.47306 7.42641 3.62956 7.50525L8.22957 9.89579C8.7016 10.1409 9.20688 10.2741 9.71883 10.3566V18.0104C9.71737 18.1139 9.73649 18.2167 9.7751 18.3128C9.8137 18.4089 9.87102 18.4963 9.94371 18.57C10.0164 18.6438 10.103 18.7023 10.1985 18.7423C10.294 18.7822 10.3965 18.8028 10.5001 18.8028C10.6036 18.8028 10.7061 18.7822 10.8016 18.7423C10.8971 18.7023 10.9838 18.6438 11.0565 18.57C11.1291 18.4963 11.1865 18.4089 11.2251 18.3128C11.2637 18.2167 11.2828 18.1139 11.2813 18.0104V10.3566C11.7933 10.2741 12.2975 10.1409 12.7696 9.89579C12.7699 9.89579 12.7702 9.89579 12.7706 9.89579L17.3706 7.50627C17.4617 7.45898 17.5426 7.3942 17.6087 7.31565C17.6748 7.23709 17.7247 7.14629 17.7557 7.04843C17.7867 6.95057 17.7981 6.84757 17.7893 6.7453C17.7804 6.64303 17.7516 6.5435 17.7043 6.45239C17.657 6.36129 17.5922 6.28039 17.5136 6.21431C17.4351 6.14824 17.3443 6.09828 17.2464 6.0673C17.1486 6.03632 17.0456 6.02491 16.9433 6.03374C16.841 6.04256 16.7415 6.07144 16.6504 6.11873L12.0494 8.50928C11.5665 8.75986 11.0393 8.88368 10.5123 8.88566C10.5031 8.88516 10.494 8.88482 10.4848 8.88464C9.95851 8.88214 9.43191 8.75967 8.94979 8.50928L4.34977 6.11975C4.23447 6.05758 4.10539 6.0254 3.97441 6.02616ZM14.4063 12.0208C14.1991 12.0208 14.0004 12.1031 13.8539 12.2497C13.7074 12.3962 13.6251 12.5949 13.6251 12.8021C13.6251 13.0093 13.7074 13.208 13.8539 13.3545C14.0004 13.501 14.1991 13.5833 14.4063 13.5833C14.6135 13.5833 14.8122 13.501 14.9588 13.3545C15.1053 13.208 15.1876 13.0093 15.1876 12.8021C15.1876 12.5949 15.1053 12.3962 14.9588 12.2497C14.8122 12.1031 14.6135 12.0208 14.4063 12.0208ZM15.9688 14.1042C15.7616 14.1042 15.5629 14.1865 15.4164 14.333C15.2699 14.4795 15.1876 14.6782 15.1876 14.8854C15.1876 15.0926 15.2699 15.2913 15.4164 15.4378C15.5629 15.5844 15.7616 15.6667 15.9688 15.6667C16.176 15.6667 16.3747 15.5844 16.5213 15.4378C16.6678 15.2913 16.7501 15.0926 16.7501 14.8854C16.7501 14.6782 16.6678 14.4795 16.5213 14.333C16.3747 14.1865 16.176 14.1042 15.9688 14.1042ZM13.3647 14.625C13.1575 14.625 12.9588 14.7073 12.8122 14.8538C12.6657 15.0003 12.5834 15.199 12.5834 15.4062C12.5834 15.6135 12.6657 15.8122 12.8122 15.9587C12.9588 16.1052 13.1575 16.1875 13.3647 16.1875C13.5719 16.1875 13.7706 16.1052 13.9171 15.9587C14.0636 15.8122 14.1459 15.6135 14.1459 15.4062C14.1459 15.199 14.0636 15.0003 13.9171 14.8538C13.7706 14.7073 13.5719 14.625 13.3647 14.625Z" fill="black"/></svg><span data-i18n="explorer">Explorer</span>',
			"page": 'blocks',
			"desiredPosition": 17,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'peers_button',
			"titleHTML": '<svg width="21" height="20" viewBox="0 0 23 22" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12.2813 0.083252C8.40631 0.083252 5.25006 3.2395 5.25006 7.1145C5.25006 7.94783 5.3954 8.745 5.66103 9.48979C5.83811 9.46895 6.02093 9.45825 6.20322 9.45825C7.15114 9.45825 7.72927 9.5835 8.28656 9.7085C8.89072 9.84391 9.51536 9.97908 11.0362 9.97908H17.4215C17.8798 9.63533 18.2444 9.36466 18.4214 9.23446C18.6402 9.06779 18.8696 8.92676 19.104 8.81738C19.2394 8.27051 19.3126 7.70304 19.3126 7.1145C19.3126 3.2395 16.1563 0.083252 12.2813 0.083252ZM12.2813 1.64575C12.7032 1.64575 13.4012 2.54679 13.797 4.24992H10.7656C11.1614 2.54679 11.8594 1.64575 12.2813 1.64575ZM9.76564 2.26526C9.51043 2.84859 9.31219 3.52596 9.17157 4.24992H7.63043C8.15126 3.41138 8.88543 2.71838 9.76564 2.26526ZM14.797 2.26526C15.6772 2.71838 16.4114 3.41138 16.9322 4.24992H15.391C15.2504 3.52596 15.0522 2.84859 14.797 2.26526ZM6.9743 5.81242H8.94777C8.91131 6.24471 8.89589 6.677 8.89589 7.1145C8.89589 7.552 8.91652 7.98429 8.94777 8.41658H6.97939C6.88043 7.99992 6.81256 7.56242 6.81256 7.1145C6.81256 6.66658 6.87534 6.22908 6.9743 5.81242ZM10.5154 5.81242H14.0473C14.0837 6.21867 14.1042 6.65096 14.1042 7.1145C14.1042 7.57804 14.0837 8.01033 14.0473 8.41658H10.5154C10.4789 8.01033 10.4584 7.57804 10.4584 7.1145C10.4584 6.65096 10.4789 6.21867 10.5154 5.81242ZM15.6148 5.81242H17.5883C17.6873 6.22908 17.7501 6.66658 17.7501 7.1145C17.7501 7.56242 17.6822 7.99992 17.5832 8.41658H15.6148C15.6461 7.98429 15.6667 7.552 15.6667 7.1145C15.6667 6.677 15.6513 6.24471 15.6148 5.81242ZM20.6736 9.46842C20.0902 9.47348 19.5361 9.70475 19.044 10.0706C18.5157 10.4631 16.9263 11.6499 15.5508 12.6768C15.2525 11.7247 14.3677 11.0208 13.323 11.0208H11.0352C9.571 11.0208 8.88836 10.9021 8.30182 10.7776C7.71528 10.6531 7.14942 10.4999 6.20119 10.4999C4.17787 10.4999 2.65232 11.7983 1.65305 12.9952C0.653793 14.1921 0.111916 15.3878 0.111916 15.3878C0.06934 15.4812 0.0455766 15.5821 0.0419831 15.6846C0.0383896 15.7872 0.0550364 15.8895 0.0909727 15.9856C0.126909 16.0817 0.181431 16.1698 0.251425 16.2449C0.321418 16.32 0.405512 16.3805 0.498904 16.423C0.592296 16.4656 0.693156 16.4893 0.795724 16.4929C0.898291 16.4965 1.00056 16.4798 1.09668 16.4438C1.19281 16.4079 1.2809 16.3533 1.35595 16.2833C1.43099 16.2133 1.4915 16.1292 1.53404 16.0358C1.53404 16.0358 2.00231 15.0154 2.85239 13.9972C3.70248 12.979 4.86462 12.0624 6.20119 12.0624C6.99358 12.0624 7.3369 12.1696 7.97731 12.3055C8.61773 12.4415 9.48112 12.5833 11.0352 12.5833H13.323C13.7639 12.5833 14.1042 12.9236 14.1042 13.3645C14.1042 13.609 13.997 13.8199 13.8285 13.9616C13.8284 13.9617 13.8072 13.9779 13.8072 13.9779C13.7918 13.9899 13.7769 14.0024 13.7624 14.0155C13.6382 14.0982 13.4878 14.1458 13.323 14.1458H9.67714C9.57362 14.1443 9.47083 14.1634 9.37476 14.202C9.2787 14.2406 9.19126 14.2979 9.11753 14.3706C9.0438 14.4433 8.98526 14.5299 8.9453 14.6254C8.90534 14.721 8.88476 14.8235 8.88476 14.927C8.88476 15.0305 8.90534 15.133 8.9453 15.2286C8.98526 15.3241 9.0438 15.4107 9.11753 15.4834C9.19126 15.5561 9.2787 15.6134 9.37476 15.652C9.47083 15.6906 9.57362 15.7097 9.67714 15.7083H13.323C13.8137 15.7083 14.2701 15.5525 14.6485 15.2902C14.681 15.2724 14.7123 15.2523 14.742 15.2301C14.742 15.2301 19.1959 11.9043 19.9758 11.3249C19.9758 11.3246 19.9758 11.3242 19.9758 11.3239C20.2697 11.1054 20.5156 11.0324 20.6879 11.0309C20.8602 11.0294 20.9888 11.0712 21.167 11.2496C21.4745 11.5569 21.4727 12.0286 21.1731 12.3401C18.384 14.6854 16.6295 16.2483 15.4704 17.1639C14.2969 18.091 13.8047 18.3124 13.323 18.3124C11.4208 18.3124 9.31291 17.7916 7.07298 17.7916C5.77089 17.7916 4.85567 18.4914 4.36505 19.1455C3.87442 19.7997 3.71096 20.467 3.71096 20.467C3.68338 20.5674 3.67616 20.6723 3.68971 20.7756C3.70327 20.8788 3.73733 20.9783 3.78989 21.0682C3.84245 21.1581 3.91246 21.2366 3.99579 21.2991C4.07912 21.3615 4.17409 21.4067 4.27513 21.4319C4.37617 21.4571 4.48123 21.4619 4.58414 21.4459C4.68705 21.43 4.78573 21.3936 4.87438 21.339C4.96303 21.2843 5.03986 21.2125 5.10036 21.1277C5.16086 21.043 5.2038 20.947 5.22666 20.8454C5.22666 20.8454 5.32361 20.471 5.61423 20.0835C5.90486 19.696 6.29173 19.3541 7.07298 19.3541C9.05596 19.3541 11.1543 19.8749 13.323 19.8749C14.3136 19.8749 15.1901 19.3772 16.4388 18.3907C17.6718 17.4167 19.4039 15.8697 22.1761 13.5385C22.2101 13.5146 22.242 13.4881 22.2717 13.4591C22.2753 13.4555 22.2763 13.4505 22.2799 13.4469L22.2819 13.4489L22.3094 13.4205C22.3856 13.341 22.4442 13.2464 22.4813 13.1427C23.1305 12.23 23.088 10.9605 22.2717 10.1449C21.8401 9.71289 21.2571 9.4634 20.6736 9.46842Z" fill="black"/></svg><span data-i18n="peers">Peers</span>',
			"page": 'peers',
			"desiredPosition": 18,
		};
		NRS.addSimpleSidebarMenuItem(options);

		options = {
			"id": 'settings',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 21 21" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.4999 0.083252C9.67861 0.083252 8.88671 0.187121 8.13073 0.362996C7.97308 0.399792 7.83076 0.484644 7.72343 0.605826C7.6161 0.727008 7.54905 0.878536 7.53157 1.03947L7.36575 2.55111C7.31152 3.04646 7.02538 3.48474 6.59366 3.73417C6.16279 3.98311 5.63981 4.01068 5.18375 3.81047H5.18273L3.79418 3.1991C3.64607 3.13392 3.48138 3.11633 3.32285 3.14877C3.16432 3.1812 3.01978 3.26206 2.90917 3.38017C1.82975 4.53095 1.0013 5.92443 0.530833 7.4797C0.48397 7.63449 0.486298 7.80002 0.537498 7.95343C0.588697 8.10685 0.686244 8.2406 0.816681 8.33622L2.04857 9.23954C2.45084 9.53529 2.68741 10.0014 2.68741 10.4999C2.68741 10.9987 2.45085 11.4652 2.04857 11.7603L0.816681 12.6626C0.686244 12.7582 0.588697 12.892 0.537498 13.0454C0.486298 13.1988 0.48397 13.3643 0.530833 13.5191C1.00125 15.0742 1.82904 16.4687 2.90917 17.6197C3.0199 17.7376 3.1645 17.8183 3.32302 17.8505C3.48155 17.8828 3.64617 17.865 3.79418 17.7997L5.18273 17.1884C5.63902 16.9877 6.1625 17.0166 6.59366 17.2657C7.02538 17.5151 7.31152 17.9534 7.36575 18.4487L7.53157 19.9604C7.54919 20.121 7.61617 20.2722 7.72329 20.3932C7.83041 20.5141 7.9724 20.5989 8.12971 20.6358C8.88604 20.8123 9.67861 20.9166 10.4999 20.9166C11.3212 20.9166 12.1131 20.8127 12.8691 20.6368C13.0267 20.6 13.1691 20.5152 13.2764 20.394C13.3837 20.2728 13.4508 20.1213 13.4683 19.9604L13.6341 18.4487C13.6883 17.9534 13.9744 17.5151 14.4062 17.2657C14.837 17.0167 15.36 16.9881 15.8161 17.1884L17.2056 17.7997C17.3536 17.865 17.5183 17.8828 17.6768 17.8505C17.8353 17.8183 17.9799 17.7376 18.0906 17.6197C19.1701 16.4689 19.9985 15.0744 20.469 13.5191C20.5158 13.3643 20.5135 13.1988 20.4623 13.0454C20.4111 12.892 20.3136 12.7582 20.1831 12.6626L18.9512 11.7603C18.549 11.4652 18.3124 10.9987 18.3124 10.4999C18.3124 10.0011 18.549 9.53464 18.9512 9.23954L20.1831 8.33724C20.3136 8.24161 20.4111 8.10787 20.4623 7.95445C20.5135 7.80103 20.5158 7.63551 20.469 7.48071C19.9985 5.92545 19.1701 4.53095 18.0906 3.38017C17.9799 3.26223 17.8353 3.18157 17.6768 3.14932C17.5183 3.11707 17.3536 3.13481 17.2056 3.20011L15.8161 3.81148C15.36 4.0117 14.837 3.98311 14.4062 3.73417C13.9744 3.48474 13.6883 3.04646 13.6341 2.55111L13.4683 1.03947C13.4506 0.878853 13.3836 0.727649 13.2765 0.606681C13.1694 0.485714 13.0274 0.400933 12.8701 0.364014C12.1138 0.1875 11.3212 0.083252 10.4999 0.083252ZM10.4999 1.64575C11.0074 1.64575 11.4947 1.73684 11.9831 1.82275L12.0807 2.72099C12.189 3.71001 12.7639 4.58968 13.6249 5.08712C14.4865 5.58488 15.5354 5.64211 16.4457 5.24174L17.2718 4.87858C17.906 5.64023 18.4082 6.50054 18.759 7.44308L18.0266 7.98018C17.2247 8.56842 16.7499 9.50498 16.7499 10.4999C16.7499 11.4949 17.2247 12.4314 18.0266 13.0197L18.759 13.5568C18.4082 14.4993 17.906 15.3596 17.2718 16.1213L16.4457 15.7581C15.5354 15.3577 14.4865 15.415 13.6249 15.9127C12.7639 16.4102 12.189 17.2898 12.0807 18.2789L11.9831 19.1771C11.4947 19.2628 11.0071 19.3541 10.4999 19.3541C9.99246 19.3541 9.50516 19.263 9.01675 19.1771L8.9191 18.2789C8.81083 17.2898 8.23589 16.4102 7.37491 15.9127C6.51336 15.415 5.46444 15.3577 4.55407 15.7581L3.72806 16.1213C3.09365 15.3597 2.59155 14.4994 2.24083 13.5568L2.97326 13.0197C3.77515 12.4314 4.24991 11.4949 4.24991 10.4999C4.24991 9.50498 3.77476 8.56782 2.97326 7.97917L2.24083 7.44206C2.59175 6.49915 3.09441 5.63942 3.72908 4.87756L4.55407 5.24072C5.46444 5.64109 6.51336 5.58488 7.37491 5.08712C8.23589 4.58968 8.81083 3.71001 8.9191 2.72099L9.01675 1.82275C9.50512 1.73708 9.99271 1.64575 10.4999 1.64575ZM10.4999 6.33325C8.20797 6.33325 6.33324 8.20799 6.33324 10.4999C6.33324 12.7919 8.20797 14.6666 10.4999 14.6666C12.7918 14.6666 14.6666 12.7919 14.6666 10.4999C14.6666 8.20799 12.7918 6.33325 10.4999 6.33325ZM10.4999 7.89575C11.9474 7.89575 13.1041 9.05242 13.1041 10.4999C13.1041 11.9474 11.9474 13.1041 10.4999 13.1041C9.05241 13.1041 7.89574 11.9474 7.89574 10.4999C7.89574 9.05242 9.05241 7.89575 10.4999 7.89575Z" fill="black"/></svg><span data-i18n="settings">Settings</span>',
			"page": 'settings',
			"desiredPosition": 19,
		};
		NRS.addSimpleSidebarMenuItem(options);
		options = {
			"id": 'generators',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 21 21" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4.90381 4.90381C4.32719 5.48043 4.0007 6.26436 4.00228 7.08345L4 11.6207H4.00228V17.9154C4.0007 18.734 4.32665 19.5179 4.90381 20.0951C5.48042 20.6717 6.26435 20.9982 7.08344 20.9966H17.9154C18.7345 20.9982 19.5184 20.6717 20.0951 20.0951C20.6722 19.5179 21.0014 18.7321 20.9966 17.9109L20.9977 10.8048L20.9966 10.8037L20.9989 7.08345L21 7.0823C20.9976 6.27449 20.6806 5.49477 20.1087 4.91751C19.5293 4.32821 18.7372 4.00036 17.9166 4.00115H15.8967V4.00343L7.08572 4.00001C6.26503 3.99842 5.48096 4.32666 4.90381 4.90381ZM6.07236 6.07237C6.34709 5.79764 6.69818 5.65167 7.08344 5.65242H9.97973L9.98087 9.97175L5.65355 9.96946L5.6547 7.08116L5.65355 7.08002C5.65278 6.69319 5.7971 6.34763 6.07236 6.07237ZM5.6547 11.623L9.98201 11.6253L9.98657 19.3442H7.08116L7.08002 19.3453C6.69315 19.3461 6.34762 19.2018 6.07236 18.9265C5.79764 18.6518 5.65394 18.3051 5.6547 17.9177V11.623ZM11.631 5.65356L15.8864 5.6547L15.8613 9.97289L11.6333 9.97175L11.631 5.65356ZM17.5388 5.6547H17.9177C18.3101 5.65432 18.6577 5.7983 18.9311 6.07693L18.9345 6.08036C19.2032 6.35134 19.3465 6.69978 19.3476 7.08687L19.3442 9.97517H17.5137L17.5388 5.6547ZM11.6344 11.6253L19.3442 11.6276V17.92C19.3464 18.3 19.2012 18.6518 18.9265 18.9265C18.6512 19.2018 18.3057 19.3461 17.9188 19.3453L17.9177 19.3442H11.639L11.6344 11.6253Z" fill="black"/></svg><span data-i18n="generators">Generators</span>',
			"page": 'generators',
			"desiredPosition": 20,
		};
		NRS.addSimpleSidebarMenuItem(options);

	};

	$(document).on("click", "#transactions_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		NRS.buildTransactionsSubTypeNavi();
  		NRS.pageNumber = 1;
		NRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_sub_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		NRS.pageNumber = 1;
		NRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_show_hide_btn", function(e) {
		e.preventDefault();
        var subTypeNaviBox = $('#transactions_sub_type_navi_box');
        if (subTypeNaviBox.is(':visible')) {
			subTypeNaviBox.hide();
			$(this).text($.t('show_type_menu', 'Show Type Menu'));
		} else {
			subTypeNaviBox.show();
			$(this).text($.t('hide_type_menu', 'Hide Type Menu'));
		}
	});

	return NRS;
}(NRS || {}, jQuery));
