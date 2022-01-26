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
var NRS = (function(NRS, $) {
    var _messages;
    var _latestMessages;

    NRS.resetMessagesState = function () {
        _messages = {};
        _latestMessages = {};
	};
	NRS.resetMessagesState();

	NRS.pages.messages = function(callback) {
		_messages = {};
        $("#inline_message_form").hide();
        $("#message_details").empty();
        $("#no_message_selected").show();
		$(".content.content-stretch:visible").width($(".page:visible").width());

		NRS.sendRequest("getBlockchainTransactions+", {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 75,
			"type": 1,
			"subtype": 0
		}, function(response) {
			if (response.transactions && response.transactions.length) {
				for (var i = 0; i < response.transactions.length; i++) {
					var otherUser = (response.transactions[i].recipient == NRS.account ? response.transactions[i].sender : response.transactions[i].recipient);
					if (!(otherUser in _messages)) {
						_messages[otherUser] = [];
					}
					_messages[otherUser].push(response.transactions[i]);
				}
				displayMessageSidebar(callback);
			} else {
				$("#no_message_selected").hide();
				$("#no_messages_available").show();
				$("#messages_sidebar").empty();
				NRS.pageLoaded(callback);
			}
		});
	};

	NRS.setup.messages = function() {
		var options = {
			"id": 'sidebar_messages',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 21 21" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M6.0875 3C4.39083 3 3 4.39083 3 6.0875V14.1625C3 15.8592 4.39083 17.25 6.0875 17.25H6.8V19.8625C6.8 20.7963 7.95297 21.3725 8.7 20.8125L13.45 17.25H18.9125C20.6092 17.25 22 15.8592 22 14.1625V6.0875C22 4.39083 20.6092 3 18.9125 3H6.0875ZM6.0875 4.425H18.9125C19.8388 4.425 20.575 5.16122 20.575 6.0875V14.1625C20.575 15.0888 19.8388 15.825 18.9125 15.825H13.2125C13.0582 15.8251 12.9082 15.8752 12.7848 15.9679L8.225 19.3875V16.5375C8.22498 16.3485 8.14991 16.1673 8.01629 16.0337C7.88268 15.9001 7.70146 15.825 7.5125 15.825H6.0875C5.16122 15.825 4.425 15.0888 4.425 14.1625V6.0875C4.425 5.16122 5.16122 4.425 6.0875 4.425ZM9.175 9.175C8.65012 9.175 8.225 9.60012 8.225 10.125C8.225 10.6499 8.65012 11.075 9.175 11.075C9.69987 11.075 10.125 10.6499 10.125 10.125C10.125 9.60012 9.69987 9.175 9.175 9.175ZM12.5 9.175C11.9751 9.175 11.55 9.60012 11.55 10.125C11.55 10.6499 11.9751 11.075 12.5 11.075C13.0249 11.075 13.45 10.6499 13.45 10.125C13.45 9.60012 13.0249 9.175 12.5 9.175ZM15.825 9.175C15.3001 9.175 14.875 9.60012 14.875 10.125C14.875 10.6499 15.3001 11.075 15.825 11.075C16.3499 11.075 16.775 10.6499 16.775 10.125C16.775 9.60012 16.3499 9.175 15.825 9.175Z" fill="black"/></svg><span data-i18n="messages">Messages</span>',
			"page": 'messages',
			"desiredPosition": 30,
			"depends": { tags: [ NRS.constants.API_TAGS.MESSAGES ] }
		};
		NRS.addSimpleSidebarMenuItem(options);
	};

	function displayMessageSidebar(callback) {
		var activeAccount = false;
		var messagesSidebar = $("#messages_sidebar");
		var $active = messagesSidebar.find("a.active");
		if ($active.length) {
			activeAccount = $active.data("account");
		}

		var rows = "";
		var sortedMessages = [];
		for (var otherUser in _messages) {
			if (!_messages.hasOwnProperty(otherUser)) {
				continue;
			}
			_messages[otherUser].sort(function (a, b) {
				if (a.timestamp > b.timestamp) {
					return 1;
				} else if (a.timestamp < b.timestamp) {
					return -1;
				} else {
					return 0;
				}
			});

			var otherUserRS = (otherUser == _messages[otherUser][0].sender ? _messages[otherUser][0].senderRS : _messages[otherUser][0].recipientRS);
			sortedMessages.push({
				"timestamp": _messages[otherUser][_messages[otherUser].length - 1].timestamp,
				"user": otherUser,
				"userRS": otherUserRS
			});
		}

		sortedMessages.sort(function (a, b) {
			if (a.timestamp < b.timestamp) {
				return 1;
			} else if (a.timestamp > b.timestamp) {
				return -1;
			} else {
				return 0;
			}
		});

		for (var i = 0; i < sortedMessages.length; i++) {
			var sortedMessage = sortedMessages[i];
			var extra = "";
			if (sortedMessage.user in NRS.contacts) {
				extra = "data-contact='" + NRS.getAccountTitle(sortedMessage, "user") + "' data-context='messages_sidebar_update_context'";
			}
			rows += "<a href='#' class='list-group-item' data-account='" + NRS.getAccountFormatted(sortedMessage, "user") + "' data-account-id='" + NRS.getAccountFormatted(sortedMessage.user) + "' " + extra + ">" +
				"<h4 class='list-group-item-heading'>" + NRS.getAccountTitle(sortedMessage, "user") + "</h4>" +
				"<p class='list-group-item-text'>" + NRS.formatTimestamp(sortedMessage.timestamp) + "</p></a>";
		}
		messagesSidebar.empty().append(rows);
		if (activeAccount) {
			messagesSidebar.find("a[data-account=" + activeAccount + "]").addClass("active").trigger("click");
		}
		NRS.pageLoaded(callback);
	}

	NRS.incoming.messages = function(transactions) {
		if (NRS.hasTransactionUpdates(transactions)) {
			if (transactions.length) {
				for (var i=0; i<transactions.length; i++) {
					var trans = transactions[i];
					if (trans.confirmed && trans.type == 1 && trans.subtype == 0 && trans.senderRS != NRS.accountRS) {
						if (trans.height >= NRS.lastBlockHeight - 3 && !_latestMessages[trans.transaction]) {
							_latestMessages[trans.transaction] = trans;
							$.growl($.t("you_received_message", {
								"account": NRS.getAccountFormatted(trans, "sender"),
								"name": NRS.getAccountTitle(trans, "sender")
							}), {
								"type": "success"
							});
						}
					}
				}
			}
			if (NRS.currentPage == "messages") {
				NRS.loadPage("messages");
			}
		}
	};

	$("#messages_sidebar").on("click", "a", function(e) {
		e.preventDefault();
		$("#messages_sidebar").find("a.active").removeClass("active");
		$(this).addClass("active");
		var otherUser = $(this).data("account-id");
		$("#no_message_selected, #no_messages_available").hide();
		$("#inline_message_recipient").val(otherUser);
		$("#inline_message_form").show();

		var last_day = "";
		var output = "<dl class='chat'>";
		var messages = _messages[otherUser];
		if (messages) {
			for (var i = 0; i < messages.length; i++) {
				var decoded = false;
				var extra = "";
				var type = "";
				if (!messages[i].attachment) {
					decoded = $.t("message_empty");
				} else if (messages[i].attachment.encryptedMessage) {
					try {
						decoded = NRS.tryToDecryptMessage(messages[i]);
						extra = "decrypted";
					} catch (err) {
						if (err.errorCode && err.errorCode == 1) {
							decoded = $.t("error_decryption_passphrase_required");
							extra = "to_decrypt";
						} else {
							decoded = $.t("error_decryption_unknown");
						}
					}
				} else if (messages[i].attachment.message) {
					if (!messages[i].attachment["version.Message"] && !messages[i].attachment["version.PrunablePlainMessage"]) {
						try {
							decoded = converters.hexStringToString(messages[i].attachment.message);
						} catch (err) {
							//legacy
							if (messages[i].attachment.message.indexOf("feff") === 0) {
								decoded = NRS.convertFromHex16(messages[i].attachment.message);
							} else {
								decoded = NRS.convertFromHex8(messages[i].attachment.message);
							}
						}
					} else {
						decoded = String(messages[i].attachment.message);
					}
				} else if (messages[i].attachment.messageHash || messages[i].attachment.encryptedMessageHash) {
					decoded = $.t("message_pruned");
				} else {
					decoded = $.t("message_empty");
				}
				if (decoded !== false) {
					if (!decoded) {
						decoded = $.t("message_empty");
					}
					decoded = String(decoded).escapeHTML().nl2br();
					if (extra == "to_decrypt") {
						decoded = "<i class='fa fa-warning'></i> " + decoded;
					} else if (extra == "decrypted") {
						if (type == "payment") {
							decoded = "<strong>+" + NRS.formatAmount(messages[i].amountNQT) + " BENED</strong><br />" + decoded;
						}
						decoded = "<i class='fa fa-lock'></i> " + decoded;
					}
				} else {
					decoded = "<i class='fa fa-warning'></i> " + $.t("error_could_not_decrypt_message");
					extra = "decryption_failed";
				}
				var day = NRS.formatTimestamp(messages[i].timestamp, true);
				if (day != last_day) {
					output += "<dt><strong>" + day + "</strong></dt>";
					last_day = day;
				}
				output += "<dd class='" + (messages[i].recipient == NRS.account ? "from" : "to") + (extra ? " " + extra : "") + "'><p>" + decoded + "</p></dd>";
			}
		}
		output += "</dl>";
		$("#message_details").empty().append(output);
        var splitter = $('#messages_page').find('.content-splitter-right-inner');
        splitter.scrollTop(splitter[0].scrollHeight);
	});

	$("#messages_sidebar_context").on("click", "a", function(e) {
		e.preventDefault();
		var account = NRS.getAccountFormatted(NRS.selectedContext.data("account"));
		var option = $(this).data("option");
		NRS.closeContextMenu();
		if (option == "add_contact") {
			$("#add_contact_account_id").val(account).trigger("blur");
			$("#add_contact_modal").modal("show");
		} else if (option == "send_cointech") {
			$("#send_money_recipient").val(account).trigger("blur");
			$("#send_money_modal").modal("show");
		} else if (option == "account_info") {
			NRS.showAccountModal(account);
		}
	});

	$("#messages_sidebar_update_context").on("click", "a", function(e) {
		e.preventDefault();
		var account = NRS.getAccountFormatted(NRS.selectedContext.data("account"));
		var option = $(this).data("option");
		NRS.closeContextMenu();
		if (option == "update_contact") {
			$("#update_contact_modal").modal("show");
		} else if (option == "send_cointech") {
			$("#send_money_recipient").val(NRS.selectedContext.data("contact")).trigger("blur");
			$("#send_money_modal").modal("show");
		}
	});

	$("body").on("click", "a[data-goto-messages-account]", function(e) {
		e.preventDefault();
		var account = $(this).data("goto-messages-account");
		NRS.goToPage("messages", function(){ $('#message_sidebar').find('a[data-account=' + account + ']').trigger('click'); });
	});

	NRS.forms.sendMessage = function($modal) {
		var data = NRS.getFormData($modal.find("form:first"));
		var converted = $modal.find("input[name=converted_account_id]").val();
		if (converted) {
			data.recipient = converted;
		}
		return {
			"data": data
		};
	};

	$("#inline_message_form").submit(function(e) {
		e.preventDefault();
        var passpharse = $("#inline_message_password").val();
        var data = {
			"recipient": $.trim($("#inline_message_recipient").val()),
			"feeBENED": "0.150000 or 1%",
			"deadline": "1440",
			"secretPhrase": $.trim(passpharse)
		};

		if (!NRS.rememberPassword) {
			if (passpharse == "") {
				$.growl($.t("error_passphrase_required"), {
					"type": "danger"
				});
				return;
			}
			var accountId = NRS.getAccountId(data.secretPhrase);
			if (accountId != NRS.account) {
				$.growl($.t("error_passphrase_incorrect"), {
					"type": "danger"
				});
				return;
			}
		}

		data.message = $.trim($("#inline_message_text").val());
		var $btn = $("#inline_message_submit");
		$btn.button("loading");
		var requestType = "sendMessage";
		if ($("#inline_message_encrypt").is(":checked")) {
			data.encrypt_message = true;
		}
		if (data.message) {
			try {
				data = NRS.addMessageData(data, "sendMessage");
			} catch (err) {
				$.growl(String(err.message).escapeHTML(), {
					"type": "danger"
				});
				return;
			}
		} else {
			data["_extra"] = {
				"message": data.message
			};
		}

		NRS.sendRequest(requestType, data, function(response) {
			if (response.errorCode) {
				$.growl(NRS.translateServerError(response).escapeHTML(), {
					type: "danger"
				});
			} else if (response.fullHash) {
				$.growl($.t("success_message_sent"), {
					type: "success"
				});
				$("#inline_message_text").val("");
				if (data["_extra"].message && data.encryptedMessageData) {
					NRS.addDecryptedTransaction(response.transaction, {
						"encryptedMessage": String(data["_extra"].message)
					});
				}

                NRS.addUnconfirmedTransaction(response.transaction, function (alreadyProcessed) {
                    if (!alreadyProcessed) {
                        $("#message_details").find("dl.chat").append("<dd class='to tentative" + (data.encryptedMessageData ? " decrypted" : "") + "'><p>" + (data.encryptedMessageData ? "<i class='fa fa-lock'></i> " : "") + (!data["_extra"].message ? $.t("message_empty") : String(data["_extra"].message).escapeHTML()) + "</p></dd>");
                        var splitter = $('#messages_page').find('.content-splitter-right-inner');
                        splitter.scrollTop(splitter[0].scrollHeight);
                    }
                });
				//leave password alone until user moves to another page.
			} else {
				//TODO
				$.growl($.t("error_send_message"), {
					type: "danger"
				});
			}
			$btn.button("reset");
		});
	});

	NRS.forms.sendMessageComplete = function(response, data) {
		data.message = data._extra.message;
		if (!(data["_extra"] && data["_extra"].convertedAccount)) {
			$.growl($.t("success_message_sent") + " <a href='#' data-account='" + NRS.getAccountFormatted(data, "recipient") + "' data-toggle='modal' data-target='#add_contact_modal' style='text-decoration:underline'>" + $.t("add_recipient_to_contacts_q") + "</a>", {
				"type": "success"
			});
		} else {
			$.growl($.t("success_message_sent"), {
				"type": "success"
			});
		}
	};

	$("#message_details").on("click", "dd.to_decrypt", function() {
		$("#messages_decrypt_modal").modal("show");
	});

	NRS.forms.decryptMessages = function($modal) {
		var data = NRS.getFormData($modal.find("form:first"));
		var success = false;
		try {
			var messagesToDecrypt = [];
			for (var otherUser in _messages) {
				if (!_messages.hasOwnProperty(otherUser)) {
					continue;
				}
				for (var key in _messages[otherUser]) {
					if (!_messages[otherUser].hasOwnProperty(key)) {
						continue;
					}
					var message = _messages[otherUser][key];
					if (message.attachment && message.attachment.encryptedMessage) {
						messagesToDecrypt.push(message);
					}
				}
			}
			success = NRS.decryptAllMessages(messagesToDecrypt, data.secretPhrase);
		} catch (err) {
			if (err.errorCode && err.errorCode <= 2) {
				return {
					"error": err.message.escapeHTML()
				};
			} else {
				return {
					"error": $.t("error_messages_decrypt")
				};
			}
		}

		if (data.rememberPassword) {
			NRS.setDecryptionPassword(data.secretPhrase);
		}
		$("#messages_sidebar").find("a.active").trigger("click");
		if (success) {
			$.growl($.t("success_messages_decrypt"), {
				"type": "success"
			});
		} else {
			$.growl($.t("error_messages_decrypt"), {
				"type": "danger"
			});
		}
		return {
			"stop": true
		};
	};

	return NRS;
}(NRS || {}, jQuery));
